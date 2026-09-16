package com.example.demo.service;

import com.example.demo.entity.Order;
import com.example.demo.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MatchingEngineService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public MatchingEngineService(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Transactional
    public void processPendingOrders(String symbol, double currentPrice) {
        List<Order> pendingOrders = orderRepository.findBySymbolAndStatus(symbol, "PENDING");

        if (pendingOrders.isEmpty()) return;

        for (Order order : pendingOrders) {
            boolean shouldExecute = false;
            double target = order.getTargetPrice();
            String side = order.getSide() != null ? order.getSide() : order.getType();
            String type = order.getOrderType();

            if ("LIMIT".equalsIgnoreCase(type)) {
                if ("BUY".equalsIgnoreCase(side) && currentPrice <= target) shouldExecute = true;
                if ("SELL".equalsIgnoreCase(side) && currentPrice >= target) shouldExecute = true;
            } else if ("STOP_LOSS".equalsIgnoreCase(type)) {
                if ("BUY".equalsIgnoreCase(side) && currentPrice >= target) shouldExecute = true;
                if ("SELL".equalsIgnoreCase(side) && currentPrice <= target) shouldExecute = true;
            } else if ("TAKE_PROFIT".equalsIgnoreCase(type)) {
                if ("BUY".equalsIgnoreCase(side) && currentPrice <= target) shouldExecute = true;
                if ("SELL".equalsIgnoreCase(side) && currentPrice >= target) shouldExecute = true;
            }

            if (shouldExecute) {
                orderService.executePendingOrder(order, currentPrice);
            }
        }
    }
}