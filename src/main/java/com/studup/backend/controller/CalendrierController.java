package com.studup.backend.controller;

import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.model.dto.request.OverrideScheduleRequest;
import com.studup.backend.model.dto.response.AlternanceScheduleResponse;
import com.studup.backend.service.CalendrierService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendrier")
public class CalendrierController {

    private final CalendrierService calendrierService;

    public CalendrierController(CalendrierService calendrierService) {
        this.calendrierService = calendrierService;
    }

    // Retourne le calendrier de compatibilité colorisé entre deux alternants
    @GetMapping("/compatibilite")
    public ResponseEntity<List<SemaineCompatibilite>> getCompatibilite(
            @RequestParam UUID user1,
            @RequestParam UUID user2) {
        return ResponseEntity.ok(calendrierService.getCalendrierCompatibilite(user1, user2));
    }

    // Modifie manuellement le label d'une semaine du calendrier de l'utilisateur connecté
    @PatchMapping("/{profileId}/semaines/{semaine}")
    public ResponseEntity<AlternanceScheduleResponse> overrideSemaine(
            @PathVariable UUID profileId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semaine,
            @Valid @RequestBody OverrideScheduleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                calendrierService.overrideSemaine(
                        userDetails.getUsername(), profileId, semaine, request));
    }
}
