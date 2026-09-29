package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000", "https://trading-frontend-lolc.onrender.com"}, allowCredentials = "true")
public class UserController {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public UserController(UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getUserBalance(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .map(user -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", user.getId(),
                        "balance", user.getBalance()
                )))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "message", "User not found"
                )));
    }

    @PostMapping("/deposit")
    @Transactional
    public ResponseEntity<Map<String, Object>> depositFunds(Principal principal, @RequestParam double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deposit amount must be greater than zero."));
        }

        try {
                User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found."));

            user.setBalance(user.getBalance() + amount);
            userRepository.save(user);

            Map<String, Object> response = Map.of(
                    "message", "Successfully deposited $" + String.format("%.2f", amount),
                    "userId", user.getId(),
                    "newBalance", user.getBalance()
            );

            messagingTemplate.convertAndSend("/topic/user/" + user.getId() + "/balance", (Object) Map.of("balance", user.getBalance()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}