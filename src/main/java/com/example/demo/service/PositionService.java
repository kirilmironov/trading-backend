package com.example.demo.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PositionService(PositionRepository positionRepository,
                           UserRepository userRepository,
                           StockRepository stockRepository,
                           OrderRepository orderRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public List<Position> getOpenPositionsByUserId(Long userId) {
        return positionRepository.findByUserIdAndStatus(userId, "OPEN");
    }

    @Transactional
    public void closePosition(Long positionId) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("Position not found"));

        Stock stock = stockRepository.findById(position.getSymbol())
                .orElseThrow(() -> new RuntimeException("Stock price not found for symbol: " + position.getSymbol()));

        closePosition(positionId, stock.getPrice());
    }

    @Transactional
    public void closePosition(Long positionId, double currentPrice) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("Position not found"));

        if ("CLOSED".equalsIgnoreCase(position.getStatus())) {
            throw new RuntimeException("Position is already closed.");
        }

        User user = position.getUser();
        double pnl = 0.0;

        if ("LONG".equalsIgnoreCase(position.getSide())) {
            pnl = (currentPrice - position.getEntryPrice()) * position.getQuantity();
        } else if ("SHORT".equalsIgnoreCase(position.getSide())) {
            pnl = (position.getEntryPrice() - currentPrice) * position.getQuantity();
        }

        double returnedAmount = (position.getQuantity() * position.getEntryPrice()) + pnl;
        user.setBalance(user.getBalance() + returnedAmount);
        userRepository.save(user);

        position.setStatus("CLOSED");
        position.setClosedAt(LocalDateTime.now());
        positionRepository.save(position);

        List<Order> pendingOrders = orderRepository.findByUserIdAndStatus(user.getId(), "PENDING");
        for (Order order : pendingOrders) {
            if (order.getSymbol().equalsIgnoreCase(position.getSymbol())) {
                order.setStatus("CANCELLED");
                orderRepository.save(order);
            }
        }

        Map<String, Object> posPayload = new HashMap<>();
        posPayload.put("action", "CLOSED");
        posPayload.put("positionId", positionId);
        posPayload.put("pnl", pnl);
        posPayload.put("newBalance", user.getBalance());

        messagingTemplate.convertAndSend("/topic/user/" + user.getUsername() + "/positions", (Object) posPayload);

        Map<String, Object> balancePayload = new HashMap<>();
        balancePayload.put("balance", user.getBalance());

        messagingTemplate.convertAndSend("/topic/user/" + user.getUsername() + "/balance", (Object) balancePayload);
    }
}