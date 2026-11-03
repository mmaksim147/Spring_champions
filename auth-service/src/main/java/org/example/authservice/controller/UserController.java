package org.example.authservice.controller;

import org.example.authservice.dto.UserProfileResponse;
import org.example.authservice.model.User;
import org.example.authservice.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles().stream().map(r -> r.getName()).collect(java.util.stream.Collectors.toSet())
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles().stream().map(r -> r.getName()).collect(java.util.stream.Collectors.toSet())
        ));
    }
}