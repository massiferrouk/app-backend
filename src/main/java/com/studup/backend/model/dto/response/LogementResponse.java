package com.studup.backend.model.dto.response;

import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.LogementStatut;
import com.studup.backend.model.enums.LogementType;
import com.studup.backend.model.enums.VilleAssociee;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LogementResponse(
        UUID id,
        UUID ownerId,
        String adresse,
        String ville,
        String codePostal,
        BigDecimal lat,
        BigDecimal lng,
        LogementType type,
        BigDecimal surface,
        Integer nbPieces,
        BigDecimal loyer,
        BigDecimal charges,
        String description,
        String[] equipements,
        LogementStatut statut,
        Boolean isVerified,
        Boolean isMeuble,
        VilleAssociee villeAssociee,
        List<String> photoUrls,
        OffsetDateTime createdAt,
        // Prénom du propriétaire — permet au frontend d'afficher son nom
        // (bouton « Contacter ») sans requête supplémentaire (relation déjà chargée).
        String ownerPrenom
) {
    public static LogementResponse from(Logement logement, List<String> photoUrls) {
        return new LogementResponse(
                logement.getId(),
                logement.getOwner().getId(),
                logement.getAdresse(),
                logement.getVille(),
                logement.getCodePostal(),
                logement.getLat(),
                logement.getLng(),
                logement.getType(),
                logement.getSurface(),
                logement.getNbPieces(),
                logement.getLoyer(),
                logement.getCharges(),
                logement.getDescription(),
                logement.getEquipements(),
                logement.getStatut(),
                logement.getIsVerified(),
                logement.getIsMeuble(),
                logement.getVilleAssociee(),
                photoUrls,
                logement.getCreatedAt(),
                logement.getOwner().getFirstName()
        );
    }
}
