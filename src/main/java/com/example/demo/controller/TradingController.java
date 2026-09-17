package com.example.demo.controller;

import com.example.demo.dto.OrderRequest;
import com.example.demo.entity.Order;
import com.example.demo.entity.Stock;
import com.example.demo.repository.StockRepository;
import com.example.demo.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "https://trading-frontend-lolc.onrender.com"})
public class TradingController {

    private final OrderService orderService;
    private final StockRepository stockRepository;

    public TradingController(OrderService orderService, StockRepository stockRepository) {
        this.orderService = orderService;
        this.stockRepository = stockRepository;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest orderReq, @RequestParam String username) {
        try {
            Order savedOrder = orderService.createOrder(orderReq, username);
            return ResponseEntity.ok(savedOrder);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Възникна грешка при обработката на поръчката."));
        }
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        try {
            orderService.cancelOrder(id);
            return ResponseEntity.ok(Map.of("message", "Поръчката бе отменена успешно!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stocks")
    public List<Stock> getStocks() {
        return stockRepository.findAll();
    }

    @GetMapping("/orders/{username}")
    public List<Order> getOrdersByUser(@PathVariable String username) {
        return orderService.getOrdersByUser(username);
    }
}