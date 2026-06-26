package com.studup.backend.controller;

import com.studup.backend.model.dto.response.AlternantDashboardResponse;
import com.studup.backend.model.dto.response.ProprietaireDashboardResponse;
import com.studup.backend.service.AlternantDashboardService;
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
    private final AlternantDashboardService alternantDashboardService;

    public DashboardController(ProprietaireDashboardService proprietaireDashboardService,
                               AlternantDashboardService alternantDashboardService) {
        this.proprietaireDashboardService = proprietaireDashboardService;
        this.alternantDashboardService = alternantDashboardService;
    }

    @GetMapping("/proprietaire")
    public ResponseEntity<ProprietaireDashboardResponse> getDashboardProprietaire(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                proprietaireDashboardService.getDashboard(userDetails.getUsername()));
    }

    @GetMapping("/alternant")
    public ResponseEntity<AlternantDashboardResponse> getDashboardAlternant(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                alternantDashboardService.getDashboard(userDetails.getUsername()));
    }
}
