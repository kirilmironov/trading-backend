package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = {"http://localhost:3000", "https://trading-frontend-lolc.onrender.com"})
public class UserController {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public UserController(UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Вземане на баланс строго по userId
    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getUserBalance(@RequestParam Long userId) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", user.getId(),
                        "balance", user.getBalance()
                )))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "message", "User not found"
                )));
    }

    // Депозиране на средства строго по userId
    @PostMapping("/deposit")
    @Transactional
    public ResponseEntity<Map<String, Object>> depositFunds(@RequestParam Long userId, @RequestParam double amount) {
        if (amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deposit amount must be greater than zero."));
        }

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

            user.setBalance(user.getBalance() + amount);
            userRepository.save(user);

            Map<String, Object> response = Map.of(
                    "message", "Successfully deposited $" + String.format("%.2f", amount),
                    "userId", user.getId(),
                    "newBalance", user.getBalance()
            );

            // WebSocket известие по userId за синхронизация с фронтенда
            messagingTemplate.convertAndSend("/topic/user/" + userId + "/balance", (Object) Map.of("balance", user.getBalance()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}