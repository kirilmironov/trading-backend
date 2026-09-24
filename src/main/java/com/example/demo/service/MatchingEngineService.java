package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.entity.Position;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MatchingEngineService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PositionRepository positionRepository;
    private final PositionService positionService;

    public MatchingEngineService(OrderRepository orderRepository, 
                                 OrderService orderService,
                                 PositionRepository positionRepository,
                                 PositionService positionService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.positionRepository = positionRepository;
        this.positionService = positionService;
    }

    @Transactional
    public void processPendingOrders(String symbol, double currentPrice) {
        // 1. Проверка на чакащи поръчки (LIMIT, TAKE_PROFIT, STOP_LOSS)
        List<Order> pendingOrders = orderRepository.findBySymbolAndStatus(symbol, "PENDING");

        if (!pendingOrders.isEmpty()) {
            for (Order order : pendingOrders) {
                if (order.getTargetPrice() == null) continue;

                boolean shouldExecute = false;
                double target = order.getTargetPrice();
                String side = order.getSide() != null ? order.getSide() : order.getType();
                String type = order.getOrderType();

                if (type == null) type = "LIMIT"; // Fallback ако е празно

                // Проверка според типа на поръчката
                if ("LIMIT".equalsIgnoreCase(type)) {
                    if ("BUY".equalsIgnoreCase(side) && currentPrice <= target) shouldExecute = true;
                    if ("SELL".equalsIgnoreCase(side) && currentPrice >= target) shouldExecute = true;
                } else if ("TAKE_PROFIT".equalsIgnoreCase(type)) {
                    // За SELL Take Profit (при long позиция) цената трябва да е >= target
                    if ("SELL".equalsIgnoreCase(side) && currentPrice >= target) shouldExecute = true;
                    // За BUY Take Profit (при short позиция) цената трябва да е <= target
                    if ("BUY".equalsIgnoreCase(side) && currentPrice <= target) shouldExecute = true;
                } else if ("STOP_LOSS".equalsIgnoreCase(type)) {
                    // За SELL Stop Loss цената трябва да паде под target
                    if ("SELL".equalsIgnoreCase(side) && currentPrice <= target) shouldExecute = true;
                    // За BUY Stop Loss цената трябва да се качи над target
                    if ("BUY".equalsIgnoreCase(side) && currentPrice >= target) shouldExecute = true;
                }

                if (shouldExecute) {
                    orderService.executePendingOrder(order, currentPrice);
                }
            }
        }

        // 2. АВТОМАТИЧНО ЗАТВАРЯНЕ НА ПОЗИЦИИ при достигнат Take Profit или Stop Loss
        List<Position> openPositions = positionRepository.findBySymbolAndStatus(symbol, "OPEN");

        for (Position pos : openPositions) {
            boolean shouldClose = false;

            if ("LONG".equalsIgnoreCase(pos.getSide())) {
                // За LONG: Take Profit при по-висока или равна цена, Stop Loss при по-ниска или равна
                if (pos.getTakeProfit() != null && currentPrice >= pos.getTakeProfit()) shouldClose = true;
                if (pos.getStopLoss() != null && currentPrice <= pos.getStopLoss()) shouldClose = true;
            } else if ("SHORT".equalsIgnoreCase(pos.getSide())) {
                // За SHORT: Take Profit при по-ниска или равна цена, Stop Loss при по-висока или равна
                if (pos.getTakeProfit() != null && currentPrice <= pos.getTakeProfit()) shouldClose = true;
                if (pos.getStopLoss() != null && currentPrice >= pos.getStopLoss()) shouldClose = true;
            }

            if (shouldClose) {
                positionService.closePosition(pos.getId(), currentPrice);
                System.out.println(">>> AUTO-CLOSED Position #" + pos.getId() + " (" + pos.getSymbol() + ") @ $" + currentPrice);
            }
        }
    }
}