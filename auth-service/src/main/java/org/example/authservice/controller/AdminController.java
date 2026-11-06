
package org.example.authservice.controller;

import org.example.authservice.dto.UpdateRolesRequest;
import org.example.authservice.dto.UserProfileResponse;
import org.example.authservice.model.Role;
import org.example.authservice.model.User;
import org.example.authservice.repository.RoleRepository;
import org.example.authservice.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AdminController(
            UserRepository userRepository,
            RoleRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserProfileResponse>> getAllUsers() {

        List<UserProfileResponse> users = userRepository.findAll()
                .stream()
                .map(this::toProfile)
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserProfileResponse> updateRoles(
            @PathVariable Long id,
            @RequestBody UpdateRolesRequest request
    ) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + id
                ));

        if (request == null ||
                request.getRoles() == null ||
                request.getRoles().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Roles must not be empty"
            );
        }

        Set<Role> roles = request.getRoles()
                .stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Role not found: " + name
                        )))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(toProfile(user));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {

        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "User not found: " + id
            );
        }

        userRepository.deleteById(id);

        return ResponseEntity.noContent().build();
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet())
        );
    }
}
