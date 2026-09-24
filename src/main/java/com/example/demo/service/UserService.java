package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 1. Регистрация на нов потребител
    @Transactional
    public User registerUser(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username '" + user.getUsername() + "' is already taken!");
        }

        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        if (user.getBalance() == 0.0) {
            user.setBalance(100000.0);
        }

        return userRepository.save(user);
    }

    // 2. Метод за логин
    public boolean loginUser(String rawPassword, String storedHashedPassword) {
        return passwordEncoder.matches(rawPassword, storedHashedPassword);
    }

    // 3. Намиране на потребител по ID
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    // 4. Помощен метод за автентикация по username
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}