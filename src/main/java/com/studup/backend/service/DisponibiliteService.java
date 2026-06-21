package com.studup.backend.service;

import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateDisponibiliteRequest;
import com.studup.backend.model.dto.response.DisponibiliteResponse;
import com.studup.backend.model.entity.Disponibilite;
import com.studup.backend.model.entity.Logement;
import com.studup.backend.model.enums.DisponibiliteType;
import com.studup.backend.repository.DisponibiliteRepository;
import com.studup.backend.repository.LogementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DisponibiliteService {

    private final DisponibiliteRepository disponibiliteRepository;
    private final LogementRepository logementRepository;

    public DisponibiliteService(DisponibiliteRepository disponibiliteRepository,
                                LogementRepository logementRepository) {
        this.disponibiliteRepository = disponibiliteRepository;
        this.logementRepository = logementRepository;
    }

    @Transactional
    public DisponibiliteResponse create(UUID logementId, CreateDisponibiliteRequest request) {
        Logement logement = logementRepository.findById(logementId)
                .orElseThrow(() -> new ResourceNotFoundException("Logement introuvable"));

        // dateDebut doit être strictement avant dateFin
        if (!request.dateDebut().isBefore(request.dateFin())) {
            throw new IllegalArgumentException("La date de début doit être antérieure à la date de fin");
        }

        // Détecte un chevauchement avec une plage existante sur ce logement
        if (disponibiliteRepository.existsOverlap(logementId, request.dateDebut(), request.dateFin())) {
            throw new IllegalStateException("Cette plage chevauche une disponibilité existante");
        }

        DisponibiliteType type = request.type() != null ? request.type() : DisponibiliteType.LIBRE;

        Disponibilite dispo = Disponibilite.builder()
                .logement(logement)
                .dateDebut(request.dateDebut())
                .dateFin(request.dateFin())
                .type(type)
                .build();

        return DisponibiliteResponse.from(disponibiliteRepository.save(dispo));
    }

    @Transactional(readOnly = true)
    public List<DisponibiliteResponse> findByLogement(UUID logementId) {
        if (!logementRepository.existsById(logementId)) {
            throw new ResourceNotFoundException("Logement introuvable");
        }
        return disponibiliteRepository.findByLogementIdOrderByDateDebutAsc(logementId)
                .stream()
                .map(DisponibiliteResponse::from)
                .toList();
    }
}
