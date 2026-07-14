package com.studup.backend.controller;

import com.studup.backend.model.dto.response.AddressSuggestionResponse;
import com.studup.backend.service.GeocodingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Autocomplétion d'adresses pour le formulaire de logement.
 * Sert de proxy vers la Base Adresse Nationale (évite les soucis CORS côté
 * Flutter web et centralise l'appel externe).
 */
@RestController
@RequestMapping("/api/v1/geocoding")
public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    // GET /api/v1/geocoding/autocomplete?q=rue clovis
    @GetMapping("/autocomplete")
    public ResponseEntity<List<AddressSuggestionResponse>> autocomplete(
            @RequestParam("q") String query) {
        return ResponseEntity.ok(geocodingService.autocomplete(query, 5));
    }
}
