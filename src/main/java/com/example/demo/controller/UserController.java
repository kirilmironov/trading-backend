package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = {"http://localhost:3000", "https://trading-frontend-lolc.onrender.com"})
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> depositFunds(@RequestParam String username, @RequestParam double amount) {
        if (amount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deposit amount must be greater than zero."));
        }

        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setBalance(user.getBalance() + amount);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Successfully deposited $" + String.format("%.2f", amount),
                    "newBalance", user.getBalance()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }
}