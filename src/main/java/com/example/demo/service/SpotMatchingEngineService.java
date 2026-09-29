package com.example.demo.service;

import com.example.demo.entity.spot.SpotOrder;
import com.example.demo.repository.spot.SpotOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SpotMatchingEngineService {

    private final SpotOrderRepository orderRepository;
    private final SpotOrderService orderService;

    public SpotMatchingEngineService(SpotOrderRepository orderRepository, SpotOrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Transactional
    public void processPendingOrders(String symbol, double currentPrice) {
        List<SpotOrder> pendingOrders = orderRepository.findBySymbolAndStatus(symbol, "PENDING");

        if (!pendingOrders.isEmpty()) {
            for (SpotOrder order : pendingOrders) {
                if (order.getTargetPrice() == null) continue;

                boolean shouldExecute = false;
                double target = order.getTargetPrice();
                String side = order.getSide();
                String type = order.getOrderType();

                if (type == null) type = "LIMIT";

                if ("LIMIT".equalsIgnoreCase(type)) {
                    if ("BUY".equalsIgnoreCase(side) && currentPrice <= target) shouldExecute = true;
                    if ("SELL".equalsIgnoreCase(side) && currentPrice >= target) shouldExecute = true;
                }

                if (shouldExecute) {
                    orderService.executePendingOrder(order, currentPrice);
                }
            }
        }
    }
}