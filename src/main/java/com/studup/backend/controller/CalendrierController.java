package com.studup.backend.controller;

import com.studup.backend.algorithm.SemaineCompatibilite;
import com.studup.backend.service.CalendrierService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendrier")
public class CalendrierController {

    private final CalendrierService calendrierService;

    public CalendrierController(CalendrierService calendrierService) {
        this.calendrierService = calendrierService;
    }

    @GetMapping("/compatibilite")
    public ResponseEntity<List<SemaineCompatibilite>> getCompatibilite(
            @RequestParam UUID user1,
            @RequestParam UUID user2) {
        return ResponseEntity.ok(calendrierService.getCalendrierCompatibilite(user1, user2));
    }
}
