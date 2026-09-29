package com.example.demo.controller;

import com.example.demo.dto.spot.SpotOcoOrderRequest;
import com.example.demo.entity.spot.SpotOcoOrder;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.SpotOcoOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/spot/oco")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000", "https://trading-frontend-lolc.onrender.com"}, allowCredentials = "true")
public class SpotOcoController {
    private final SpotOcoOrderService ocoOrderService;
    private final UserRepository userRepository;

    public SpotOcoController(SpotOcoOrderService ocoOrderService, UserRepository userRepository) {
        this.ocoOrderService = ocoOrderService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SpotOcoOrderRequest request, Principal principal) {
        try {
            return ResponseEntity.ok(ocoOrderService.create(request, currentUserId(principal)));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<SpotOcoOrder>> list(Principal principal) {
        return ResponseEntity.ok(ocoOrderService.getByUserId(currentUserId(principal)));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> cancel(@PathVariable Long orderId, Principal principal) {
        try {
            ocoOrderService.cancel(orderId, currentUserId(principal));
            return ResponseEntity.ok(Map.of("message", "OCO order cancelled"));
        } catch (RuntimeException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }

    private Long currentUserId(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found.")).getId();
    }
}
