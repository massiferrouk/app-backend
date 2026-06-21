package com.studup.backend.controller;

import com.studup.backend.model.dto.request.CreateDisponibiliteRequest;
import com.studup.backend.model.dto.response.DisponibiliteResponse;
import com.studup.backend.service.DisponibiliteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logements/{logementId}/disponibilites")
public class DisponibiliteController {

    private final DisponibiliteService disponibiliteService;

    public DisponibiliteController(DisponibiliteService disponibiliteService) {
        this.disponibiliteService = disponibiliteService;
    }

    // Crée une plage de disponibilité — réservé au propriétaire du logement
    @PreAuthorize("@securityService.isLogementOwner(#logementId, authentication)")
    @PostMapping
    public ResponseEntity<DisponibiliteResponse> create(
            @PathVariable UUID logementId,
            @Valid @RequestBody CreateDisponibiliteRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(disponibiliteService.create(logementId, request));
    }

    // Liste toutes les plages d'un logement — public
    @GetMapping
    public ResponseEntity<List<DisponibiliteResponse>> list(@PathVariable UUID logementId) {
        return ResponseEntity.ok(disponibiliteService.findByLogement(logementId));
    }
}
