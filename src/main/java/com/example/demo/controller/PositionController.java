package com.example.demo.controller;

import com.example.demo.entity.Position;
import com.example.demo.service.PositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/positions")
@CrossOrigin(origins = {"http://localhost:3000", "https://trading-frontend-lolc.onrender.com"})
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping("/open")
    public ResponseEntity<List<Position>> getOpenPositions(@RequestParam Long userId) {
        return ResponseEntity.ok(positionService.getOpenPositionsByUserId(userId));
    }

    @PostMapping("/close/{positionId}")
    public ResponseEntity<?> closePosition(@PathVariable Long positionId) {
        try {
            positionService.closePosition(positionId);
            return ResponseEntity.ok(Map.of("message", "Position closed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "An unexpected error occurred while closing position."));
        }
    }
}