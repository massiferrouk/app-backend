package com.studup.backend.controller;

import com.studup.backend.model.dto.response.ProprietaireDashboardResponse;
import com.studup.backend.service.ProprietaireDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final ProprietaireDashboardService proprietaireDashboardService;

    public DashboardController(ProprietaireDashboardService proprietaireDashboardService) {
        this.proprietaireDashboardService = proprietaireDashboardService;
    }

    @GetMapping("/proprietaire")
    public ResponseEntity<ProprietaireDashboardResponse> getDashboardProprietaire(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                proprietaireDashboardService.getDashboard(userDetails.getUsername()));
    }
}
