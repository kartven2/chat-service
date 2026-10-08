package com.example.chat.controller;

import com.example.chat.domain.User;
import com.example.chat.repository.UserRepository;
import com.example.chat.security.ChatUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User profile REST API (stateless per README).
 * GET /api/users/me      → own profile
 * GET /api/users/{id}    → public profile of any user
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(
            @AuthenticationPrincipal ChatUserPrincipal principal) {
        return userRepository.findById(principal.getUserId())
                .map(this::toPublicProfile)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(this::toPublicProfile)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toPublicProfile(User u) {
        return Map.of(
                "id",        u.getId(),
                "username",  u.getUsername(),
                "email",     u.getEmail(),
                "createdAt", u.getCreatedAt()
        );
    }
}
