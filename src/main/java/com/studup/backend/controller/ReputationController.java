package com.studup.backend.controller;

import com.studup.backend.model.dto.response.ReputationScoreResponse;
import com.studup.backend.service.ReputationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reputation")
public class ReputationController {

    private final ReputationService reputationService;

    public ReputationController(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    // Retourne le score de réputation et le badge d'un utilisateur
    @GetMapping("/user/{userId}")
    public ResponseEntity<ReputationScoreResponse> getScore(@PathVariable UUID userId) {
        return ResponseEntity.ok(reputationService.getScore(userId));
    }
}
