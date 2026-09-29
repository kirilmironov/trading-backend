package com.example.demo.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpotPositionService {
    private final SpotPositionRepository positionRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final SpotOrderRepository orderRepository;
    private final SpotOcoOrderRepository ocoOrderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SpotPositionService(SpotPositionRepository positionRepository, UserRepository userRepository,
                               StockRepository stockRepository, SpotOrderRepository orderRepository,
                               SpotOcoOrderRepository ocoOrderRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.orderRepository = orderRepository;
        this.ocoOrderRepository = ocoOrderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<SpotPosition> getOpenPositionsByUserId(Long userId) {
        return positionRepository.findByUserIdAndStatus(userId, "OPEN");
    }

    @Transactional
    public void closePosition(Long positionId, Long userId) {
        SpotPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("Position not found"));
        if (position.getUser() == null || !userId.equals(position.getUser().getId())) {
            throw new RuntimeException("Position not found");
        }
        Stock stock = stockRepository.findById(position.getSymbol())
                .orElseThrow(() -> new RuntimeException("Stock price not found for symbol: " + position.getSymbol()));
        closePosition(position, stock.getPrice());
    }

    private void closePosition(SpotPosition position, double currentPrice) {
        if ("CLOSED".equalsIgnoreCase(position.getStatus())) throw new RuntimeException("Position is already closed.");
        if (!Double.isFinite(currentPrice) || currentPrice <= 0) throw new IllegalArgumentException("Current spot price is unavailable.");

        User user = position.getUser();
        double returnedAmount = position.getQuantity() * currentPrice;
        user.setBalance(user.getBalance() + returnedAmount);
        userRepository.save(user);

        double pnl = (currentPrice - position.getEntryPrice()) * position.getQuantity();
        position.setStatus("CLOSED");
        position.setQuantity(0);
        position.setClosedAt(LocalDateTime.now());
        positionRepository.save(position);

        List<SpotOrder> pendingOrders = orderRepository.findByUserIdAndStatus(user.getId(), "PENDING");
        for (SpotOrder order : pendingOrders) {
            if (order.getSymbol().equalsIgnoreCase(position.getSymbol())) {
                order.setStatus("CANCELLED");
                orderRepository.save(order);
            }
        }
        List<SpotOcoOrder> pendingOcoOrders = ocoOrderRepository.findByUserIdAndStatus(user.getId(), "PENDING");
        for (SpotOcoOrder order : pendingOcoOrders) {
            if ("SELL".equalsIgnoreCase(order.getSide()) && order.getSymbol().equalsIgnoreCase(position.getSymbol())) {
                order.setStatus("CANCELLED");
                order.setClosedAt(LocalDateTime.now());
                ocoOrderRepository.save(order);
            }
        }

        Map<String, Object> positionPayload = new HashMap<>();
        positionPayload.put("action", "CLOSED");
        positionPayload.put("positionId", position.getId());
        positionPayload.put("pnl", pnl);
        positionPayload.put("newBalance", user.getBalance());
        messagingTemplate.convertAndSend("/topic/user/" + user.getId() + "/positions", (Object) positionPayload);
        messagingTemplate.convertAndSend("/topic/user/" + user.getId() + "/balance", (Object) Map.of("balance", user.getBalance()));
    }
}
