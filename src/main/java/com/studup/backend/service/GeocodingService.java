package com.studup.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private final RestClient restClient;

    public GeocodingService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "StudUp/1.0 contact@studup.fr")
                .build();
    }

    /**
     * Géocode une adresse française via Nominatim (OpenStreetMap).
     * Retourne null si l'adresse est introuvable — pas bloquant pour la création du logement.
     */
    @SuppressWarnings("unchecked")
    public Coordinates geocode(String adresse, String ville, String codePostal) {
        String query = adresse + ", " + codePostal + " " + ville + ", France";
        try {
            List<Map<String, Object>> results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", "1")
                            .queryParam("countrycodes", "fr")
                            .build())
                    .retrieve()
                    .body(List.class);

            if (results == null || results.isEmpty()) {
                log.warn("Adresse introuvable via Nominatim : {}", query);
                return null;
            }

            Map<String, Object> first = results.get(0);
            return new Coordinates(
                    new BigDecimal(first.get("lat").toString()),
                    new BigDecimal(first.get("lon").toString())
            );
        } catch (Exception e) {
            log.warn("Erreur géocodage Nominatim pour '{}' : {}", query, e.getMessage());
            return null;
        }
    }

    public record Coordinates(BigDecimal lat, BigDecimal lng) {}
}
