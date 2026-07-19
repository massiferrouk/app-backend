package com.studup.backend.controller;

import com.studup.backend.model.dto.request.AssocierVilleRequest;
import com.studup.backend.model.dto.request.CreateLogementRequest;
import com.studup.backend.model.dto.request.LogementSearchRequest;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.model.dto.response.PageResponse;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.service.LogementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logements")
public class LogementController {

    private final LogementService logementService;

    public LogementController(LogementService logementService) {
        this.logementService = logementService;
    }

    // GET /api/v1/logements?ville=Paris&loyer_max=800&surface_min=20&meuble=true&type=STUDIO&tri=prix_asc&page=0
    @GetMapping
    public ResponseEntity<PageResponse<LogementResponse>> search(
            @RequestParam(required = false) String ville,
            @RequestParam(name = "loyer_max", required = false) java.math.BigDecimal loyerMax,
            @RequestParam(name = "surface_min", required = false) java.math.BigDecimal surfaceMin,
            @RequestParam(required = false) Boolean meuble,
            @RequestParam(required = false) LogementType type,
            @RequestParam(required = false) String tri,
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal UserDetails userDetails) {

        LogementSearchRequest request = new LogementSearchRequest(ville, loyerMax, surfaceMin, meuble, type, tri, page);
        return ResponseEntity.ok(logementService.search(request, userDetails.getUsername()));
    }

    // Tous les logements de l'utilisateur connecté, brouillons inclus (APP-70)
    @GetMapping("/mes-logements")
    public ResponseEntity<List<LogementResponse>> getMesLogements(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                logementService.getMesLogements(userDetails.getUsername()));
    }

    // Crée un logement en statut BROUILLON
    @PostMapping
    public ResponseEntity<LogementResponse> createLogement(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateLogementRequest request) {

        LogementResponse response = logementService.createLogement(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Récupère le détail d'un logement
    @GetMapping("/{id}")
    public ResponseEntity<LogementResponse> getLogement(@PathVariable UUID id) {
        return ResponseEntity.ok(logementService.getLogement(id));
    }

    // Passe le logement en statut ACTIF — vérifie ownership avant d'entrer dans la méthode
    @PreAuthorize("@securityService.isLogementOwner(#id, authentication)")
    @PutMapping("/{id}/publish")
    public ResponseEntity<LogementResponse> publishLogement(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        LogementResponse response = logementService.publishLogement(userDetails.getUsername(), id);
        return ResponseEntity.ok(response);
    }

    // Associe le logement à VILLE_A ou VILLE_B — vérifie ownership avant d'entrer dans la méthode
    @PreAuthorize("@securityService.isLogementOwner(#id, authentication)")
    @PatchMapping("/{id}/ville")
    public ResponseEntity<LogementResponse> associerVille(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody AssocierVilleRequest request) {

        LogementResponse response = logementService.associerVille(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(response);
    }

    // Upload de 1 à 10 photos — vérifie ownership avant d'entrer dans la méthode
    @PreAuthorize("@securityService.isLogementOwner(#id, authentication)")
    @PostMapping("/{id}/photos")
    public ResponseEntity<List<String>> addPhotos(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) {

        List<String> urls = logementService.addPhotos(userDetails.getUsername(), id, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(urls);
    }

    // Modification d'un logement (brouillon ou publié) — ownership vérifié
    @PreAuthorize("@securityService.isLogementOwner(#id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<LogementResponse> updateLogement(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody CreateLogementRequest request) {

        return ResponseEntity.ok(
                logementService.updateLogement(userDetails.getUsername(), id, request));
    }

    // Suppression d'un logement — ownership vérifié avant d'entrer dans la méthode
    @PreAuthorize("@securityService.isLogementOwner(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLogement(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        logementService.deleteLogement(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
