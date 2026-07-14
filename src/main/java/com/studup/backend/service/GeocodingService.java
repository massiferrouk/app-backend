package com.studup.backend.service;

import com.studup.backend.model.dto.response.AddressSuggestionResponse;
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
    // Base Adresse Nationale — API officielle française pour l'autocomplétion
    private final RestClient banClient;

    public GeocodingService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "StudUp/1.0 contact@studup.fr")
                .build();
        this.banClient = RestClient.builder()
                .baseUrl("https://api-adresse.data.gouv.fr")
                .build();
    }

    /**
     * Autocomplétion d'adresses françaises via la Base Adresse Nationale.
     * Retourne au plus [limit] suggestions structurées (adresse, ville, CP,
     * coordonnées). Liste vide si la requête est trop courte ou en cas d'erreur
     * (jamais bloquant).
     */
    @SuppressWarnings("unchecked")
    public List<AddressSuggestionResponse> autocomplete(String query, int limit) {
        if (query == null || query.trim().length() < 3) {
            return List.of();
        }
        try {
            Map<String, Object> body = banClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/")
                            .queryParam("q", query)
                            .queryParam("limit", limit)
                            .queryParam("autocomplete", 1)
                            .build())
                    .retrieve()
                    .body(Map.class);

            if (body == null) return List.of();
            List<Map<String, Object>> features =
                    (List<Map<String, Object>>) body.getOrDefault("features", List.of());

            return features.stream().map(this::toSuggestion).toList();
        } catch (Exception e) {
            log.warn("Erreur autocomplétion BAN pour '{}' : {}", query, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private AddressSuggestionResponse toSuggestion(Map<String, Object> feature) {
        Map<String, Object> props = (Map<String, Object>) feature.getOrDefault("properties", Map.of());
        Map<String, Object> geom = (Map<String, Object>) feature.getOrDefault("geometry", Map.of());

        BigDecimal lat = null;
        BigDecimal lng = null;
        // GeoJSON : coordinates = [lon, lat]
        if (geom.get("coordinates") instanceof List<?> coords && coords.size() == 2) {
            lng = new BigDecimal(coords.get(0).toString());
            lat = new BigDecimal(coords.get(1).toString());
        }

        return new AddressSuggestionResponse(
                str(props.get("label")),
                str(props.get("name")),      // numéro + voie
                str(props.get("city")),
                str(props.get("postcode")),
                lat,
                lng
        );
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
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
