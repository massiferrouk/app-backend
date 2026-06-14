package com.studup.backend.controller;

import com.studup.backend.model.dto.request.CreateAlternantProfileRequest;
import com.studup.backend.model.dto.response.AlternantProfileResponse;
import com.studup.backend.service.AlternantProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class AlternantProfileController {

    private final AlternantProfileService alternantProfileService;

    public AlternantProfileController(AlternantProfileService alternantProfileService) {
        this.alternantProfileService = alternantProfileService;
    }

    // @AuthenticationPrincipal injecte l'utilisateur connecté depuis le SecurityContext.
    // getUsername() retourne l'email — c'est ce que notre JwtAuthFilter a mis en place.
    @PostMapping("/alternant")
    public ResponseEntity<AlternantProfileResponse> createProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateAlternantProfileRequest request) {

        AlternantProfileResponse response = alternantProfileService
                .createProfile(userDetails.getUsername(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/alternant")
    public ResponseEntity<AlternantProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateAlternantProfileRequest request) {

        AlternantProfileResponse response = alternantProfileService
                .updateProfile(userDetails.getUsername(), request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/alternant")
    public ResponseEntity<AlternantProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        AlternantProfileResponse response = alternantProfileService
                .getProfile(userDetails.getUsername());

        return ResponseEntity.ok(response);
    }
}
