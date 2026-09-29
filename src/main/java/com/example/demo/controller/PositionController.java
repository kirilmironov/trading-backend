package com.example.demo.controller;

import com.example.demo.entity.spot.SpotPosition;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.SpotPositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/positions")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000", "https://trading-frontend-lolc.onrender.com"}, allowCredentials = "true")
public class PositionController {

    private final SpotPositionService positionService;
    private final UserRepository userRepository;

    public PositionController(SpotPositionService positionService, UserRepository userRepository) {
        this.positionService = positionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/open")
    public ResponseEntity<List<SpotPosition>> getOpenPositions(Principal principal) {
        Long userId = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found.")).getId();
        return ResponseEntity.ok(positionService.getOpenPositionsByUserId(userId));
    }

    @PostMapping("/close/{positionId}")
        public ResponseEntity<?> closePosition(@PathVariable Long positionId, Principal principal) {
        try {
            Long userId = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found.")).getId();
            positionService.closePosition(positionId, userId);
            return ResponseEntity.ok(Map.of("message", "Position closed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "An unexpected error occurred while closing position."));
        }
    }
}