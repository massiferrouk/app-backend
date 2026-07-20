package com.studup.backend.controller;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.ChangeModeRequest;
import com.studup.backend.model.dto.response.UserResponse;
import com.studup.backend.repository.UserRepository;
import com.studup.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    // Identité de l'utilisateur connecté — pour l'écran profil mobile (APP-78)
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    // Change le mode du compte connecté : étudiant ⇄ alternant uniquement (APP-117).
    // PROPRIETAIRE et ADMIN sont rejetés côté service (UserService.changeMode).
    @PatchMapping("/me/role")
    public ResponseEntity<UserResponse> changeMode(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangeModeRequest request) {
        return ResponseEntity.ok(
                userService.changeMode(userDetails.getUsername(), request.role()));
    }
}
