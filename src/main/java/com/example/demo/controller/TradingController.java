package com.example.demo.controller;

import com.example.demo.dto.spot.SpotOrderRequest;
import com.example.demo.entity.Stock;
import com.example.demo.entity.spot.SpotOrder;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.SpotOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000", "https://trading-frontend-lolc.onrender.com"}, allowCredentials = "true")
public class TradingController {

    private final SpotOrderService orderService;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    public TradingController(SpotOrderService orderService, StockRepository stockRepository, UserRepository userRepository) {
        this.orderService = orderService;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
    }

    // --- ORDERS ENDPOINTS ---

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody SpotOrderRequest orderReq, Principal principal) {
        try {
            Long userId = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found.")).getId();
            SpotOrder savedOrder = orderService.createOrder(orderReq, userId);
            return ResponseEntity.ok(savedOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "An error occurred while processing the order."));
        }
    }

    @DeleteMapping("/orders/{id}")
        public ResponseEntity<?> cancelOrder(@PathVariable Long id, Principal principal) {
        try {
            Long userId = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found.")).getId();
            orderService.cancelOrder(id, userId);
            return ResponseEntity.ok(Map.of("message", "Order cancelled successfully!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/orders")
    public List<SpotOrder> getOrders(Principal principal) {
        Long userId = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found.")).getId();
        return orderService.getOrdersByUserId(userId);
    }

    // --- STOCKS ENDPOINTS ---

    @GetMapping("/stocks")
    public List<Stock> getStocks() {
        return stockRepository.findAll();
    }
}