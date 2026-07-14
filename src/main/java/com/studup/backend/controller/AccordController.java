package com.studup.backend.controller;

import com.studup.backend.model.dto.request.AccordRequest;
import com.studup.backend.model.dto.response.AccordResponse;
import com.studup.backend.service.AccordService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accords")
public class AccordController {

    private final AccordService accordService;

    public AccordController(AccordService accordService) {
        this.accordService = accordService;
    }

    // Envoie une demande d'accord
    @PostMapping
    public ResponseEntity<AccordResponse> createAccord(
            @Valid @RequestBody AccordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accordService.createAccord(userDetails.getUsername(), request));
    }

    // Détail d'un accord (participants uniquement — 403 sinon)
    @GetMapping("/{id}")
    public ResponseEntity<AccordResponse> getAccord(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accordService.getAccord(id, userDetails.getUsername()));
    }

    // Historique des accords de l'utilisateur connecté
    @GetMapping("/mes-accords")
    public ResponseEntity<Page<AccordResponse>> getMesAccords(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(accordService.getMesAccords(userDetails.getUsername(), pageable));
    }

    // Accepte un accord (destinataire uniquement)
    @PutMapping("/{id}/accept")
    public ResponseEntity<AccordResponse> acceptAccord(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accordService.acceptAccord(id, userDetails.getUsername()));
    }

    // Refuse un accord (destinataire uniquement)
    @PutMapping("/{id}/refuse")
    public ResponseEntity<AccordResponse> refuseAccord(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accordService.refuseAccord(id, userDetails.getUsername()));
    }

    // Annule un accord (initiateur ou destinataire)
    @PutMapping("/{id}/cancel")
    public ResponseEntity<AccordResponse> cancelAccord(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accordService.cancelAccord(id, userDetails.getUsername()));
    }
}
