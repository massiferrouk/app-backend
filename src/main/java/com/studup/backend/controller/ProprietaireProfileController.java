package com.studup.backend.controller;

import com.studup.backend.model.dto.request.CreateProprietaireProfileRequest;
import com.studup.backend.model.dto.response.ProprietaireProfileResponse;
import com.studup.backend.service.ProprietaireProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProprietaireProfileController {

    private final ProprietaireProfileService proprietaireProfileService;

    public ProprietaireProfileController(ProprietaireProfileService proprietaireProfileService) {
        this.proprietaireProfileService = proprietaireProfileService;
    }

    @PostMapping("/proprietaire")
    public ResponseEntity<ProprietaireProfileResponse> createProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateProprietaireProfileRequest request) {

        ProprietaireProfileResponse response = proprietaireProfileService
                .createProfile(userDetails.getUsername(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/proprietaire")
    public ResponseEntity<ProprietaireProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {

        ProprietaireProfileResponse response = proprietaireProfileService
                .getProfile(userDetails.getUsername());

        return ResponseEntity.ok(response);
    }
}
