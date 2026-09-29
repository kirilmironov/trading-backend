package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000", "https://trading-frontend-lolc.onrender.com"}, allowCredentials = "true")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // REGISTRATION
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request,
                                      HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Username and password are required!");
        }

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body("Username is already taken!");
        }

        // Hash the plain text password before saving to the database
        String hashedPassword = passwordEncoder.encode(password);

        // Every new user starts with a $10,000 balance
        User user = new User(username, hashedPassword, 10000.00);
        User savedUser = userRepository.save(user);
        authenticate(savedUser, servletRequest, servletResponse);
        return ResponseEntity.ok(userResponse(savedUser));
    }

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        String username = request.get("username");
        String password = request.get("password");

        Optional<User> userOpt = username == null || password == null
                ? Optional.empty()
                : userRepository.findByUsername(username);

        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {
            User user = userOpt.get();
            authenticate(user, servletRequest, servletResponse);
            return ResponseEntity.ok(userResponse(user));
        }

        return ResponseEntity.status(401).body("Invalid username or password!");
    }
    
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(userResponse(user)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    private void authenticate(User user, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private Map<String, Object> userResponse(User user) {
        return Map.of("id", user.getId(), "username", user.getUsername(), "balance", user.getBalance());
    }
}