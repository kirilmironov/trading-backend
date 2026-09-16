package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    } // Инжектираме енкодера от WebSecurityConfig

    // 1. Метод за регистрация
    public User registerUser(User user) {
        // Хешираме чистата парола преди записа
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);
        
        return userRepository.save(user);
    }

    // 2. Метод за логин
    public boolean loginUser(String rawPassword, String storedHashedPassword) {
        // Сравняваме въведената парола с хеша от базата
        return passwordEncoder.matches(rawPassword, storedHashedPassword);
    }
}