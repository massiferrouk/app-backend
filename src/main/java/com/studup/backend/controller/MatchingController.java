package com.studup.backend.controller;

import com.studup.backend.model.dto.response.MatchingSuggestionResponse;
import com.studup.backend.service.MatchingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    // Retourne les 20 meilleures suggestions de matching pour l'utilisateur connecté
    @GetMapping("/suggestions")
    public ResponseEntity<List<MatchingSuggestionResponse>> getSuggestions(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(matchingService.getSuggestions(userDetails.getUsername()));
    }
}
