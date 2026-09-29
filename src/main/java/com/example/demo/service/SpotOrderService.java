package com.example.demo.service;

import com.example.demo.dto.spot.SpotOrderRequest;
import com.example.demo.entity.Stock;
import com.example.demo.entity.User;
import com.example.demo.entity.spot.SpotOrder;
import com.example.demo.entity.spot.SpotOcoOrder;
import com.example.demo.entity.spot.SpotPosition;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.spot.SpotOrderRepository;
import com.example.demo.repository.spot.SpotOcoOrderRepository;
import com.example.demo.repository.spot.SpotPositionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SpotOrderService {
    private final SpotOrderRepository orderRepository;
    private final SpotOcoOrderRepository ocoOrderRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final SpotPositionRepository positionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SpotOrderService(SpotOrderRepository orderRepository, SpotOcoOrderRepository ocoOrderRepository,
                            StockRepository stockRepository,
                            UserRepository userRepository, SpotPositionRepository positionRepository,
                            SimpMessagingTemplate messagingTemplate) {
        this.orderRepository = orderRepository;
        this.ocoOrderRepository = ocoOrderRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public SpotOrder createOrder(SpotOrderRequest request, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        Stock stock = stockRepository.findById(request.getSymbol()).orElseThrow(() -> new RuntimeException("Stock not found"));
        String side = request.getSide() == null ? "" : request.getSide().trim().toUpperCase();
        String orderType = request.getOrderType() == null ? "MARKET" : request.getOrderType().trim().toUpperCase();
        double currentPrice = stock.getPrice();
        double quantity = request.getQuantity();
        validateRequest(side, orderType, request.getTargetPrice(), currentPrice, quantity);

        Double targetPrice = request.getTargetPrice();
        boolean marketableLimit = "LIMIT".equals(orderType)
                && (("BUY".equals(side) && targetPrice >= currentPrice) || ("SELL".equals(side) && targetPrice <= currentPrice));
        double validationPrice = "LIMIT".equals(orderType) && !marketableLimit ? targetPrice : currentPrice;
        if ("BUY".equals(side) && availableQuoteBalance(user) + 1e-8 < validationPrice * quantity) {
            throw new IllegalArgumentException("Insufficient balance for spot buy.");
        }
        if ("SELL".equals(side) && availableBaseQuantity(user, request.getSymbol()) + 1e-8 < quantity) {
            throw new IllegalArgumentException("Insufficient spot quantity to sell.");
        }

        boolean executeImmediately = "MARKET".equals(orderType) || marketableLimit;
        SpotOrder order = new SpotOrder();
        order.setUser(user);
        order.setSymbol(request.getSymbol());
        order.setQuantity(quantity);
        order.setSide(side);
        order.setOrderType(orderType);
        order.setTargetPrice(targetPrice);
        order.setTimestamp(LocalDateTime.now());
        order.setStatus(executeImmediately ? "EXECUTED" : "PENDING");
        if (executeImmediately) {
            executeSpotTrade(user, request.getSymbol(), side, quantity, currentPrice);
            order.setPrice(currentPrice);
        }

        SpotOrder savedOrder = orderRepository.save(order);
        publishOrder(savedOrder);
        if (executeImmediately) publishBalance(user);
        return savedOrder;
    }

    @Transactional
    public SpotOrder cancelOrder(Long id, Long userId) {
        SpotOrder order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found!"));
        if (order.getUser() == null || !userId.equals(order.getUser().getId())) throw new IllegalArgumentException("Order not found!");
        if (!"PENDING".equalsIgnoreCase(order.getStatus())) throw new IllegalArgumentException("Only PENDING orders can be cancelled!");
        order.setStatus("CANCELLED");
        SpotOrder savedOrder = orderRepository.save(order);
        publishOrder(savedOrder);
        return savedOrder;
    }

    @Transactional
    public void executePendingOrder(SpotOrder order, double currentPrice) {
        if (order == null || !"PENDING".equalsIgnoreCase(order.getStatus()) || !Double.isFinite(currentPrice) || currentPrice <= 0) return;
        String side = order.getSide() == null ? "" : order.getSide().trim().toUpperCase();
        double quantity = order.getQuantity();
        User user = order.getUser();
        if (!"BUY".equals(side) && !"SELL".equals(side)) return;
        if (user == null || !Double.isFinite(quantity) || quantity <= 0 || !Double.isFinite(currentPrice * quantity)) return;
        if ("BUY".equals(side) && availableQuoteBalanceExcludingSpotOrder(user, order) + 1e-8 < currentPrice * quantity) return;
        if ("SELL".equals(side) && availableBaseQuantityExcludingSpotOrder(user, order.getSymbol(), order) + 1e-8 < quantity) return;

        executeSpotTrade(user, order.getSymbol(), side, quantity, currentPrice);
        order.setPrice(currentPrice);
        order.setStatus("EXECUTED");
        order.setTimestamp(LocalDateTime.now());
        orderRepository.save(order);
        publishOrder(order);
        publishBalance(user);
    }

    public List<SpotOrder> getOrdersByUserId(Long userId) { return orderRepository.findByUserId(userId); }

    public double calculateOwnedQuantity(User user, String symbol) {
        return positionRepository.findByUserIdAndStatus(user.getId(), "OPEN").stream()
                .filter(position -> symbol.equalsIgnoreCase(position.getSymbol()))
                .mapToDouble(SpotPosition::getQuantity).sum();
    }

    double availableQuoteBalance(User user) {
        double reservedLimit = reservedPendingBuyAmount(user.getId(), null);
        double reservedOco = ocoOrderRepository.findByUserIdAndStatus(user.getId(), "PENDING").stream()
            .filter(order -> "BUY".equalsIgnoreCase(order.getSide()))
            .mapToDouble(this::ocoBuyReserve)
            .sum();
        return user.getBalance() - reservedLimit - reservedOco;
    }

    double availableQuoteBalanceExcludingOco(User user, Long excludedOcoId) {
        double reservedLimit = reservedPendingBuyAmount(user.getId(), null);
        double reservedOco = ocoOrderRepository.findByUserIdAndStatus(user.getId(), "PENDING").stream()
                .filter(order -> excludedOcoId == null || !excludedOcoId.equals(order.getId()))
                .filter(order -> "BUY".equalsIgnoreCase(order.getSide()))
                .mapToDouble(this::ocoBuyReserve)
                .sum();
        return user.getBalance() - reservedLimit - reservedOco;
    }

    private double availableQuoteBalanceExcludingSpotOrder(User user, SpotOrder excludedOrder) {
        double reservedLimit = reservedPendingBuyAmount(user.getId(), excludedOrder);
        double reservedOco = ocoOrderRepository.findByUserIdAndStatus(user.getId(), "PENDING").stream()
                .filter(order -> "BUY".equalsIgnoreCase(order.getSide()))
                .mapToDouble(this::ocoBuyReserve)
                .sum();
        return user.getBalance() - reservedLimit - reservedOco;
    }

    double availableBaseQuantity(User user, String symbol) { return availableBaseQuantityExcludingSpotOrder(user, symbol, null); }

    double availableBaseQuantityExcludingOco(User user, String symbol, Long excludedOcoId) {
        double reservedLimit = reservedPendingSellQuantity(user.getId(), symbol, null);
        double reservedOco = ocoOrderRepository.findByUserIdAndStatus(user.getId(), "PENDING").stream()
                .filter(order -> excludedOcoId == null || !excludedOcoId.equals(order.getId()))
                .filter(order -> "SELL".equalsIgnoreCase(order.getSide()) && symbol.equalsIgnoreCase(order.getSymbol()))
                .mapToDouble(SpotOcoOrder::getQuantity)
                .sum();
        return calculateOwnedQuantity(user, symbol) - reservedLimit - reservedOco;
    }

    private double availableBaseQuantityExcludingSpotOrder(User user, String symbol, SpotOrder excludedOrder) {
        double reservedLimit = reservedPendingSellQuantity(user.getId(), symbol, excludedOrder);
        double reservedOco = ocoOrderRepository.findByUserIdAndStatus(user.getId(), "PENDING").stream()
                .filter(order -> "SELL".equalsIgnoreCase(order.getSide()) && symbol.equalsIgnoreCase(order.getSymbol()))
                .mapToDouble(SpotOcoOrder::getQuantity)
                .sum();
        return calculateOwnedQuantity(user, symbol) - reservedLimit - reservedOco;
    }

    private double ocoBuyReserve(SpotOcoOrder order) {
        return Math.max(order.getTakeProfitPrice(), order.getStopLimitPrice()) * order.getQuantity();
    }

    void executeOcoTrade(User user, String symbol, String side, double quantity, double price) {
        executeSpotTrade(user, symbol, side, quantity, price);
        publishBalance(user);
    }

    private void validateRequest(String side, String orderType, Double targetPrice, double currentPrice, double quantity) {
        if (!Double.isFinite(currentPrice) || currentPrice <= 0) throw new IllegalArgumentException("Current spot price is unavailable.");
        if (!Double.isFinite(quantity) || quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
        if (!"BUY".equals(side) && !"SELL".equals(side)) throw new IllegalArgumentException("Invalid order side.");
        if (!"MARKET".equals(orderType) && !"LIMIT".equals(orderType)) throw new IllegalArgumentException("Invalid order type.");
        if ("LIMIT".equals(orderType) && (targetPrice == null || !Double.isFinite(targetPrice) || targetPrice <= 0)) throw new IllegalArgumentException("A valid limit price is required.");
        if (!Double.isFinite(currentPrice * quantity)) throw new IllegalArgumentException("Order value is outside the supported range.");
    }

    private double reservedPendingBuyAmount(Long userId, SpotOrder excludedOrder) {
        return orderRepository.findByUserIdAndStatus(userId, "PENDING").stream()
                .filter(order -> !isExcludedOrder(order, excludedOrder))
                .filter(order -> "BUY".equalsIgnoreCase(order.getSide()) && order.getTargetPrice() != null)
                .mapToDouble(order -> order.getTargetPrice() * order.getQuantity()).sum();
    }

    private double reservedPendingSellQuantity(Long userId, String symbol, SpotOrder excludedOrder) {
        return orderRepository.findByUserIdAndStatus(userId, "PENDING").stream()
                .filter(order -> !isExcludedOrder(order, excludedOrder))
                .filter(order -> "SELL".equalsIgnoreCase(order.getSide()) && symbol.equalsIgnoreCase(order.getSymbol()))
                .mapToDouble(SpotOrder::getQuantity).sum();
    }

    private boolean isExcludedOrder(SpotOrder candidate, SpotOrder excludedOrder) {
        return excludedOrder != null && (candidate == excludedOrder || (excludedOrder.getId() != null && excludedOrder.getId().equals(candidate.getId())));
    }

    private void executeSpotTrade(User user, String symbol, String side, double quantity, double price) {
        double notional = price * quantity;
        if ("BUY".equals(side)) {
            if (user.getBalance() + 1e-8 < notional) throw new IllegalArgumentException("Insufficient balance for spot buy.");
            user.setBalance(user.getBalance() - notional);
            userRepository.save(user);
            upsertSpotPosition(user, symbol, quantity, price);
        } else {
            reduceSpotPosition(user, symbol, quantity);
            user.setBalance(user.getBalance() + notional);
            userRepository.save(user);
        }
    }

    private void upsertSpotPosition(User user, String symbol, double quantity, double price) {
        SpotPosition existing = positionRepository.findByUserIdAndStatus(user.getId(), "OPEN").stream()
                .filter(position -> symbol.equalsIgnoreCase(position.getSymbol())).findFirst().orElse(null);
        if (existing == null) {
            SpotPosition position = new SpotPosition();
            position.setUser(user);
            position.setSymbol(symbol);
            position.setQuantity(quantity);
            position.setEntryPrice(price);
            position.setStatus("OPEN");
            positionRepository.save(position);
            return;
        }
        double newQuantity = existing.getQuantity() + quantity;
        existing.setEntryPrice((existing.getQuantity() * existing.getEntryPrice() + quantity * price) / newQuantity);
        existing.setQuantity(newQuantity);
        positionRepository.save(existing);
    }

    private void reduceSpotPosition(User user, String symbol, double quantity) {
        List<SpotPosition> positions = positionRepository.findByUserIdAndStatus(user.getId(), "OPEN").stream()
                .filter(position -> symbol.equalsIgnoreCase(position.getSymbol())).toList();
        double available = positions.stream().mapToDouble(SpotPosition::getQuantity).sum();
        if (available + 1e-8 < quantity) throw new IllegalArgumentException("Insufficient spot quantity to sell.");
        double remainingToReduce = quantity;
        for (SpotPosition position : positions) {
            if (remainingToReduce <= 1e-8) break;
            double reduced = Math.min(position.getQuantity(), remainingToReduce);
            remainingToReduce -= reduced;
            position.setQuantity(position.getQuantity() - reduced);
            if (position.getQuantity() <= 1e-8) {
                position.setQuantity(0);
                position.setStatus("CLOSED");
                position.setClosedAt(LocalDateTime.now());
            }
            positionRepository.save(position);
        }
    }

    private void publishBalance(User user) {
        messagingTemplate.convertAndSend("/topic/user/" + user.getId() + "/balance", (Object) Map.of("balance", user.getBalance()));
    }

    private void publishOrder(SpotOrder order) {
        messagingTemplate.convertAndSend("/topic/user/" + order.getUser().getId() + "/orders", order);
    }
}
