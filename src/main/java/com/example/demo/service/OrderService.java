package com.example.demo.service;

import com.example.demo.dto.OrderRequest;
import com.example.demo.entity.Order;
import com.example.demo.entity.Stock;
import com.example.demo.entity.User;
import com.example.demo.repository.OrderRepository;
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
    private final SimpMessagingTemplate messagingTemplate;

    public OrderService(OrderRepository orderRepository,
                        StockRepository stockRepository,
                        UserRepository userRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Order createOrder(OrderRequest orderReq, String username) {
        Stock stock = stockRepository.findById(orderReq.getSymbol())
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

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
            executeOrderInternal(order, user, currentPrice, side, orderReq.getQuantity());
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
            updateUserBalance(user, side, currentPrice, order.getQuantity());
        }

        order.setPrice(currentPrice);
        order.setStatus("EXECUTED");
        orderRepository.save(order);

        messagingTemplate.convertAndSend("/topic/orders", order);
        System.out.println(">>> EXECUTED " + order.getOrderType() + " order #" + order.getId() + " for " + order.getSymbol() + " @ $" + currentPrice);
    }

    private void executeOrderInternal(Order order, User user, double currentPrice, String side, int quantity) {
        if ("BUY".equalsIgnoreCase(side)) {
            double totalCost = currentPrice * quantity;
            if (user.getBalance() < totalCost) {
                throw new IllegalArgumentException("Нямате достатъчно баланс за тази покупка!");
            }
        } else if ("SELL".equalsIgnoreCase(side)) {
            int ownedQuantity = calculateOwnedQuantity(user, order.getSymbol());
            if (ownedQuantity < quantity) {
                throw new IllegalArgumentException("Нямате налични бройки за продажба! Притежавате: " + ownedQuantity);
            }
        }

        updateUserBalance(user, side, currentPrice, quantity);
        order.setPrice(currentPrice);
        order.setStatus("EXECUTED");
    }

    private void createPendingOrder(Order order, User user, OrderRequest orderReq, Double targetPrice, String side) {
        if ("BUY".equalsIgnoreCase(side)) {
            double estimatedCost = targetPrice * orderReq.getQuantity();
            if (user.getBalance() < estimatedCost) {
                throw new IllegalArgumentException("Нямате достатъчно баланс за тази чакаща поръчка!");
            }
        } else if ("SELL".equalsIgnoreCase(side)) {
            int ownedQuantity = calculateOwnedQuantity(user, orderReq.getSymbol());
            if (ownedQuantity < orderReq.getQuantity()) {
                throw new IllegalArgumentException("Нямате налични бройки за пускане на тази продажба!");
            }
        }

        order.setPrice(null);
        order.setStatus("PENDING");
    }

    private void updateUserBalance(User user, String side, double price, int quantity) {
        double totalTransactionValue = price * quantity;
        if ("BUY".equalsIgnoreCase(side)) {
            user.setBalance(user.getBalance() - totalTransactionValue);
        } else if ("SELL".equalsIgnoreCase(side)) {
            user.setBalance(user.getBalance() + totalTransactionValue);
        }
        userRepository.save(user);
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Поръчката не е намерена!"));

        if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Могат да се отменят само чакащи (PENDING) поръчки!");
        }

        order.setStatus("CANCELLED");
        Order savedOrder = orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/orders", savedOrder);
        return savedOrder;
    }

    public List<Order> getOrdersByUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return orderRepository.findByUser(user);
    }

    public int calculateOwnedQuantity(User user, String symbol) {
        List<Order> userOrders = orderRepository.findByUser(user);
        int owned = 0;
        for (Order o : userOrders) {
            if ("EXECUTED".equalsIgnoreCase(o.getStatus()) && symbol.equalsIgnoreCase(o.getSymbol())) {
                String side = o.getSide() != null ? o.getSide() : o.getType();
                if ("BUY".equalsIgnoreCase(side)) {
                    owned += o.getQuantity();
                } else if ("SELL".equalsIgnoreCase(side)) {
                    owned -= o.getQuantity();
                }
            }
        }
        return owned;
    }
}