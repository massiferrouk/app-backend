package com.studup.backend.controller;

import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.model.dto.request.OverrideScheduleRequest;
import com.studup.backend.model.dto.response.AlternanceScheduleResponse;
import com.studup.backend.model.dto.response.IcalTokenResponse;
import com.studup.backend.service.CalendrierService;
import com.studup.backend.service.ICalExportService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final ICalExportService iCalExportService;

    public CalendrierController(CalendrierService calendrierService, ICalExportService iCalExportService) {
        this.calendrierService = calendrierService;
        this.iCalExportService = iCalExportService;
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

    // Télécharge le fichier .ics pour l'utilisateur connecté
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportIcal(@AuthenticationPrincipal UserDetails userDetails) {
        String content = iCalExportService.generateIcal(userDetails.getUsername());
        byte[] bytes = content.getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"studup-calendrier.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(bytes);
    }

    // URL d'abonnement persistante — accessible sans authentification
    @GetMapping("/subscribe/{token}")
    public ResponseEntity<byte[]> subscribeIcal(@PathVariable String token) {
        String content = iCalExportService.generateIcalByToken(token);
        byte[] bytes = content.getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"studup-calendrier.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(bytes);
    }

    // Retourne le token d'abonnement de l'utilisateur (ou en crée un)
    @GetMapping("/token")
    public ResponseEntity<IcalTokenResponse> getToken(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(iCalExportService.getOrCreateToken(userDetails.getUsername()));
    }

    // Régénère le token d'abonnement (révoque l'ancien)
    @PostMapping("/token/regenerate")
    public ResponseEntity<IcalTokenResponse> regenerateToken(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(iCalExportService.regenerateToken(userDetails.getUsername()));
    }
}
