package com.example.demo.service;

import com.example.demo.dto.OrderRequest;
import com.example.demo.entity.Order;
import com.example.demo.entity.Position;
import com.example.demo.entity.Stock;
import com.example.demo.entity.User;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PositionRepository;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public OrderService(OrderRepository orderRepository,
                        StockRepository stockRepository,
                        UserRepository userRepository,
                        PositionRepository positionRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Order createOrder(OrderRequest orderReq, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

        Stock stock = stockRepository.findById(orderReq.getSymbol())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        String requestedOrderType = orderReq.getOrderType() != null ? orderReq.getOrderType() : "MARKET";
        String side = orderReq.getSide() != null ? orderReq.getSide() : orderReq.getType();
        double currentPrice = stock.getPrice();
        Double targetPrice = orderReq.getTargetPrice();

        boolean shouldExecuteImmediately = "MARKET".equalsIgnoreCase(requestedOrderType);

        if ("LIMIT".equalsIgnoreCase(requestedOrderType) && targetPrice != null) {
            if ("BUY".equalsIgnoreCase(side) && targetPrice >= currentPrice) {
                shouldExecuteImmediately = true;
            } else if ("SELL".equalsIgnoreCase(side) && targetPrice <= currentPrice) {
                shouldExecuteImmediately = true;
            }
        }

        Order order = new Order();
        order.setUser(user);
        order.setSymbol(orderReq.getSymbol());
        order.setQuantity(orderReq.getQuantity());
        order.setSide(side);
        order.setType(side);
        order.setOrderType(requestedOrderType);
        order.setTargetPrice(targetPrice);
        order.setTimestamp(LocalDateTime.now());

        if (shouldExecuteImmediately) {
            executeOrderInternal(order, user, currentPrice, side, orderReq.getQuantity(), orderReq.getTakeProfit(), orderReq.getStopLoss());
        } else {
            createPendingOrder(order, user, orderReq, targetPrice, side);
        }

        Order savedOrder = orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/orders", savedOrder);
        return savedOrder;
    }

    @Transactional
    public void executePendingOrder(Order order, double currentPrice) {
        String side = order.getSide() != null ? order.getSide() : order.getType();
        User user = order.getUser();

        if (user != null) {
            updateUserBalanceAndPosition(user, order.getSymbol(), side, currentPrice, order.getQuantity(), null, null);
        }

        order.setPrice(currentPrice);
        order.setStatus("EXECUTED");
        orderRepository.save(order);

        messagingTemplate.convertAndSend("/topic/orders", order);
        System.out.println(">>> EXECUTED " + order.getOrderType() + " order #" + order.getId() + " for " + order.getSymbol() + " @ $" + currentPrice);
    }

    private void executeOrderInternal(Order order, User user, double currentPrice, String side, double quantity, Double takeProfit, Double stopLoss) {
        if ("BUY".equalsIgnoreCase(side)) {
            double totalCost = currentPrice * quantity;
            if (user.getBalance() < totalCost) {
                throw new IllegalArgumentException("Insufficient balance for this purchase!");
            }
        } else if ("SELL".equalsIgnoreCase(side)) {
            double ownedQuantity = calculateOwnedQuantity(user, order.getSymbol());
            if (ownedQuantity < quantity) {
                throw new IllegalArgumentException("Insufficient assets to sell! You currently own: " + ownedQuantity);
            }
        }

        updateUserBalanceAndPosition(user, order.getSymbol(), side, currentPrice, quantity, takeProfit, stopLoss);
        order.setPrice(currentPrice);
        order.setStatus("EXECUTED");
    }

    private void createPendingOrder(Order order, User user, OrderRequest orderReq, Double targetPrice, String side) {
        if ("BUY".equalsIgnoreCase(side)) {
            double estimatedCost = targetPrice * orderReq.getQuantity();
            if (user.getBalance() < estimatedCost) {
                throw new IllegalArgumentException("Insufficient balance for this limit order!");
            }
        } else if ("SELL".equalsIgnoreCase(side)) {
            double ownedQuantity = calculateOwnedQuantity(user, orderReq.getSymbol());
            if (ownedQuantity < orderReq.getQuantity()) {
                throw new IllegalArgumentException("Insufficient assets to place this sell order!");
            }
        }

        order.setPrice(null);
        order.setStatus("PENDING");
    }

    private void updateUserBalanceAndPosition(User user, String symbol, String side, double price, double quantity, Double takeProfit, Double stopLoss) {
        double totalTransactionValue = price * quantity;

        if ("BUY".equalsIgnoreCase(side)) {
            user.setBalance(user.getBalance() - totalTransactionValue);
            userRepository.save(user);

            Position position = new Position(user, symbol, "LONG", quantity, price, takeProfit, stopLoss);
            positionRepository.save(position);

        } else if ("SELL".equalsIgnoreCase(side)) {
            user.setBalance(user.getBalance() + totalTransactionValue);
            userRepository.save(user);

            // FIFO Намаляване / Затваряне на позиции
            List<Position> openPositions = positionRepository.findByUserIdAndStatus(user.getId(), "OPEN");
            double remainingToSell = quantity;

            for (Position pos : openPositions) {
                if (!pos.getSymbol().equalsIgnoreCase(symbol)) {
                    continue;
                }

                double posQty = pos.getQuantity();

                if (posQty <= remainingToSell) {
                    // Целият брой на позицията се затваря
                    remainingToSell -= posQty;
                    pos.setQuantity(0.0);
                    pos.setStatus("CLOSED");
                    pos.setClosedAt(LocalDateTime.now());
                    positionRepository.save(pos);
                } else {
                    // Частично намаляване (Partial Close)
                    pos.setQuantity(posQty - remainingToSell);
                    positionRepository.save(pos);
                    remainingToSell = 0.0;
                }

                if (remainingToSell <= 0.00001) {
                    break;
                }
            }

            // Отмяна на чакащи поръчки за символа, ако е продадена цялата наличност
            double currentOwned = calculateOwnedQuantity(user, symbol);
            if (currentOwned <= 0.00001) {
                List<Order> pendingOrders = orderRepository.findByUserIdAndStatus(user.getId(), "PENDING");
                for (Order pendingOrder : pendingOrders) {
                    if (pendingOrder.getSymbol().equalsIgnoreCase(symbol)) {
                        pendingOrder.setStatus("CANCELLED");
                        orderRepository.save(pendingOrder);
                        messagingTemplate.convertAndSend("/topic/orders", pendingOrder);
                    }
                }
            }
        }
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found!"));

        if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Only PENDING orders can be cancelled!");
        }

        order.setStatus("CANCELLED");
        Order savedOrder = orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/orders", savedOrder);
        return savedOrder;
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public double calculateOwnedQuantity(User user, String symbol) {
        List<Position> openPositions = positionRepository.findByUserIdAndStatus(user.getId(), "OPEN");
        double owned = 0.0;
        for (Position pos : openPositions) {
            if (symbol.equalsIgnoreCase(pos.getSymbol())) {
                owned += pos.getQuantity();
            }
        }
        return owned;
    }
}