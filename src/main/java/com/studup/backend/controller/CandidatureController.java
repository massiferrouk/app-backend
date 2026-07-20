package com.studup.backend.controller;

import com.studup.backend.model.dto.request.CreateCandidatureRequest;
import com.studup.backend.model.dto.request.UpdateCandidatureRequest;
import com.studup.backend.model.dto.response.CandidatureResponse;
import com.studup.backend.service.CandidatureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Suivi des candidatures logement (APP-117).
 * Toutes les routes agissent sur les candidatures de l'utilisateur CONNECTÉ :
 * on ne passe jamais un userId depuis le client.
 */
@RestController
@RequestMapping("/api/v1/candidatures")
public class CandidatureController {

    private final CandidatureService candidatureService;

    public CandidatureController(CandidatureService candidatureService) {
        this.candidatureService = candidatureService;
    }

    @GetMapping
    public ResponseEntity<List<CandidatureResponse>> mesCandidatures(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                candidatureService.getMesCandidatures(userDetails.getUsername()));
    }

    // Suivre une annonce. Idempotent : renvoie 201 avec la candidature
    // (créée ou déjà existante).
    @PostMapping
    public ResponseEntity<CandidatureResponse> suivre(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateCandidatureRequest request) {
        CandidatureResponse response = candidatureService.suivre(
                userDetails.getUsername(), request.logementId(), request.statut());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CandidatureResponse> updateStatut(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCandidatureRequest request) {
        return ResponseEntity.ok(candidatureService.updateStatut(
                userDetails.getUsername(), id, request.statut(), request.note()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        candidatureService.delete(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
