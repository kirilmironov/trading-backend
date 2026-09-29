package com.example.demo.service;

import com.example.demo.dto.spot.SpotOcoOrderRequest;
import com.example.demo.entity.Stock;
import com.example.demo.entity.User;
import com.example.demo.entity.spot.SpotOcoOrder;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.spot.SpotOcoOrderRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SpotOcoOrderService {
    private final SpotOcoOrderRepository ocoRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final SpotOrderService spotOrderService;
    private final SimpMessagingTemplate messagingTemplate;

    public SpotOcoOrderService(SpotOcoOrderRepository ocoRepository, StockRepository stockRepository,
                               UserRepository userRepository, SpotOrderService spotOrderService,
                               SimpMessagingTemplate messagingTemplate) {
        this.ocoRepository = ocoRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.spotOrderService = spotOrderService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public SpotOcoOrder create(SpotOcoOrderRequest request, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Stock stock = stockRepository.findById(request.getSymbol()).orElseThrow(() -> new RuntimeException("Stock not found"));
        String side = normalize(request.getSide());
        double marketPrice = stock.getPrice();
        double quantity = request.getQuantity();
        double takeProfit = requiredPrice(request.getTakeProfitPrice());
        double stop = requiredPrice(request.getStopPrice());
        double stopLimit = requiredPrice(request.getStopLimitPrice());

        if (!"BUY".equals(side) && !"SELL".equals(side)) throw new IllegalArgumentException("OCO side must be BUY or SELL.");
        if (!Double.isFinite(quantity) || quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
        if (!Double.isFinite(marketPrice) || marketPrice <= 0) throw new IllegalArgumentException("Current spot price is unavailable.");
        if (!Double.isFinite(Math.max(takeProfit, stopLimit) * quantity)) throw new IllegalArgumentException("OCO order value is outside the supported range.");
        validatePriceRelations(side, marketPrice, takeProfit, stop, stopLimit);

        if ("SELL".equals(side)) {
            if (spotOrderService.availableBaseQuantity(user, request.getSymbol()) + 1e-8 < quantity) {
                throw new IllegalArgumentException("Insufficient unreserved Spot quantity for OCO.");
            }
        } else {
            double quoteReserve = Math.max(takeProfit, stopLimit) * quantity;
            if (spotOrderService.availableQuoteBalance(user) + 1e-8 < quoteReserve) {
                throw new IllegalArgumentException("Insufficient unreserved Spot balance for OCO.");
            }
        }

        SpotOcoOrder order = new SpotOcoOrder();
        order.setUser(user);
        order.setSymbol(request.getSymbol());
        order.setSide(side);
        order.setQuantity(quantity);
        order.setTakeProfitPrice(takeProfit);
        order.setStopPrice(stop);
        order.setStopLimitPrice(stopLimit);
        order.setStatus("PENDING");
        return publish(ocoRepository.save(order));
    }

    public List<SpotOcoOrder> getByUserId(Long userId) { return ocoRepository.findByUserId(userId); }

    @Transactional
    public SpotOcoOrder cancel(Long orderId, Long userId) {
        SpotOcoOrder order = ocoRepository.findById(orderId).orElseThrow(() -> new RuntimeException("OCO order not found"));
        if (order.getUser() == null || !userId.equals(order.getUser().getId())) throw new RuntimeException("OCO order not found");
        if (!"PENDING".equalsIgnoreCase(order.getStatus())) throw new IllegalArgumentException("Only pending OCO orders can be cancelled.");
        order.setStatus("CANCELLED");
        order.setClosedAt(LocalDateTime.now());
        return publish(ocoRepository.save(order));
    }

    @Transactional
    public void processPendingOrders(String symbol, double currentPrice) {
        if (!Double.isFinite(currentPrice) || currentPrice <= 0) return;
        for (SpotOcoOrder order : ocoRepository.findPendingForUpdate(symbol, "PENDING")) {
            boolean takeProfitTriggered = "SELL".equals(order.getSide())
                    ? currentPrice >= order.getTakeProfitPrice() : currentPrice <= order.getTakeProfitPrice();
            if (takeProfitTriggered) {
                execute(order, "TAKE_PROFIT", currentPrice);
                continue;
            }

            boolean stopTriggered = "SELL".equals(order.getSide())
                    ? currentPrice <= order.getStopPrice() : currentPrice >= order.getStopPrice();
            if (stopTriggered && !order.isStopTriggered()) {
                order.setStopTriggered(true);
                ocoRepository.save(order);
            }

            boolean stopLimitExecutable = order.isStopTriggered() && ("SELL".equals(order.getSide())
                    ? currentPrice >= order.getStopLimitPrice() : currentPrice <= order.getStopLimitPrice());
            if (stopLimitExecutable) execute(order, "STOP_LOSS", currentPrice);
        }
    }

    private void execute(SpotOcoOrder order, String leg, double price) {
        User user = order.getUser();
        if ("SELL".equals(order.getSide())) {
            if (spotOrderService.availableBaseQuantityExcludingOco(user, order.getSymbol(), order.getId()) + 1e-8 < order.getQuantity()) return;
        } else {
            if (spotOrderService.availableQuoteBalanceExcludingOco(user, order.getId()) + 1e-8 < price * order.getQuantity()) return;
        }

        spotOrderService.executeOcoTrade(user, order.getSymbol(), order.getSide(), order.getQuantity(), price);
        order.setStatus("EXECUTED");
        order.setExecutedLeg(leg);
        order.setExecutionPrice(price);
        order.setClosedAt(LocalDateTime.now());
        publish(ocoRepository.save(order));
    }

    private SpotOcoOrder publish(SpotOcoOrder order) {
        messagingTemplate.convertAndSend("/topic/user/" + order.getUser().getId() + "/orders", order);
        return order;
    }

    private void validatePriceRelations(String side, double market, double takeProfit, double stop, double stopLimit) {
        boolean validSell = takeProfit > market && market > stop && stop >= stopLimit;
        boolean validBuy = takeProfit < market && market < stop && stop <= stopLimit;
        if (("SELL".equals(side) && !validSell) || ("BUY".equals(side) && !validBuy)) {
            throw new IllegalArgumentException("Invalid OCO prices for side and current market price.");
        }
    }

    private double requiredPrice(Double price) {
        if (price == null || !Double.isFinite(price) || price <= 0) throw new IllegalArgumentException("OCO prices must be positive numbers.");
        return price;
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
}
