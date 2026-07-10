package com.studup.backend.model.dto.response;

import com.studup.backend.model.enums.RythmeAlternance;

import java.util.List;
import java.util.UUID;

/**
 * Calendrier d'alternance complet de l'utilisateur connecté (APP-67).
 * Inclut le contexte du profil (villes, rythme) nécessaire à l'affichage
 * mobile, et le profileId requis pour les overrides (PATCH).
 */
public record MesSemainesResponse(
        UUID profileId,
        String villeA,
        String villeB,
        RythmeAlternance rythme,
        List<AlternanceScheduleResponse> semaines
) {}
