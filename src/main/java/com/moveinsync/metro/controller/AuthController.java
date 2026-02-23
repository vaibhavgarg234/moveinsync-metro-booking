package com.moveinsync.metro.controller;

import com.moveinsync.metro.model.User;
import com.moveinsync.metro.repository.UserRepository;
import com.moveinsync.metro.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register and login to get a JWT token")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // ── DTOs ─────────────────────────────────────────────────────────────────

    record RegisterRequest(
            @NotBlank String username,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6) String password
    ) {}

    record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    record AuthResponse(String token, String userId, String username, Set<String> roles) {}

    // ── Register ──────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user account")
    public Map<String, String> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepo.existsByUsername(req.username())) {
            return Map.of("error", "Username already taken");
        }
        if (userRepo.existsByEmail(req.email())) {
            return Map.of("error", "Email already registered");
        }

        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .roles(Set.of("ROLE_USER"))
                .build();
        userRepo.save(user);

        log.info("New user registered: {}", req.username());
        return Map.of("message", "Account created successfully. Please login.");
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT access token")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        User user = userRepo.findByUsername(req.username())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRoles());
        log.info("User logged in: {}", user.getUsername());

        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getUsername(), user.getRoles()));
    }

    // ── Me ────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get current user info from JWT")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "No token provided"));
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
        }
        String username = jwtUtil.getUsername(token);
        return userRepo.findByUsername(username)
                .map(u -> ResponseEntity.ok(Map.of(
                        "userId", u.getId(),
                        "username", u.getUsername(),
                        "email", u.getEmail(),
                        "roles", u.getRoles()
                )))
                .orElse(ResponseEntity.status(404).build());
    }
}