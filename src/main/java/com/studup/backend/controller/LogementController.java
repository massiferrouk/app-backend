package com.studup.backend.controller;

import com.studup.backend.model.dto.request.CreateLogementRequest;
import com.studup.backend.model.dto.response.LogementResponse;
import com.studup.backend.service.LogementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // Passe le logement en statut ACTIF (ownership check dans le service)
    @PutMapping("/{id}/publish")
    public ResponseEntity<LogementResponse> publishLogement(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        LogementResponse response = logementService.publishLogement(userDetails.getUsername(), id);
        return ResponseEntity.ok(response);
    }

    // Upload de 1 à 10 photos sur un logement
    @PostMapping("/{id}/photos")
    public ResponseEntity<List<String>> addPhotos(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) {

        List<String> urls = logementService.addPhotos(userDetails.getUsername(), id, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(urls);
    }
}
