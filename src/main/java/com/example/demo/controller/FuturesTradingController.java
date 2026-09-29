package com.example.demo.controller;

import com.example.demo.dto.futures.FuturesOrderRequest;
import com.example.demo.entity.futures.FuturesOrder;
import com.example.demo.entity.futures.FuturesPosition;
import com.example.demo.entity.futures.FuturesWallet;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FuturesOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/futures")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000", "https://trading-frontend-lolc.onrender.com"}, allowCredentials = "true")
public class FuturesTradingController {
    private final FuturesOrderService futuresService;
    private final UserRepository userRepository;

    public FuturesTradingController(FuturesOrderService futuresService, UserRepository userRepository) {
        this.futuresService = futuresService;
        this.userRepository = userRepository;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody FuturesOrderRequest request, Principal principal) {
        try {
            Long userId = currentUserId(principal);
            FuturesOrder order = futuresService.createMarketOrder(request, userId);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        } catch (Exception error) {
            return ResponseEntity.internalServerError().body(Map.of("message", error.getMessage()));
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<List<FuturesOrder>> orders(Principal principal) {
        return ResponseEntity.ok(futuresService.getOrders(currentUserId(principal)));
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId, Principal principal) {
        try {
            futuresService.cancelOrder(orderId, currentUserId(principal));
            return ResponseEntity.ok(Map.of("message", "Futures order canceled"));
        } catch (RuntimeException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    @GetMapping("/wallet")
    public ResponseEntity<FuturesWallet> wallet(Principal principal) {
        return ResponseEntity.ok(futuresService.getWallet(currentUserId(principal)));
    }

    @PostMapping("/wallet/transfer")
    public ResponseEntity<?> transfer(@RequestParam String direction, @RequestParam double amount, Principal principal) {
        try {
            return ResponseEntity.ok(futuresService.transfer(currentUserId(principal), direction, amount));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    @PostMapping("/leverage")
    public ResponseEntity<?> changeLeverage(@RequestParam String symbol, @RequestParam int leverage, Principal principal) {
        try {
            return ResponseEntity.ok(futuresService.changeLeverage(currentUserId(principal), symbol, leverage));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    @GetMapping("/positions/open")
    public ResponseEntity<List<FuturesPosition>> openPositions(Principal principal) {
        return ResponseEntity.ok(futuresService.getOpenPositions(currentUserId(principal)));
    }

    @PostMapping("/positions/close/{positionId}")
    public ResponseEntity<?> closePosition(@PathVariable Long positionId, Principal principal) {
        try {
            futuresService.closePosition(positionId, currentUserId(principal));
            return ResponseEntity.ok(Map.of("message", "Futures position closed successfully"));
        } catch (RuntimeException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    private Long currentUserId(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found.")).getId();
    }
}
