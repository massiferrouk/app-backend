package com.studup.backend.model.dto.request;

import com.studup.backend.model.enums.LogementType;

import java.math.BigDecimal;

/**
 * Paramètres de recherche de logements — tous optionnels sauf page.
 * Passés en query params : GET /api/v1/logements?ville=Paris&loyer_max=800
 */
public record LogementSearchRequest(
        String ville,
        BigDecimal loyerMax,
        BigDecimal surfaceMin,
        Boolean meuble,
        LogementType type,
        String tri,
        Integer page
) {}
