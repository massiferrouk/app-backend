package com.studup.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeocodingServiceTest {

    private final GeocodingService geocodingService = new GeocodingService();

    @Test
    void shouldReturnEmptyWhenQueryTooShort() {
        // Moins de 3 caractères → pas d'appel externe, liste vide
        assertThat(geocodingService.autocomplete("ru", 5)).isEmpty();
        assertThat(geocodingService.autocomplete("", 5)).isEmpty();
        assertThat(geocodingService.autocomplete(null, 5)).isEmpty();
    }
}
