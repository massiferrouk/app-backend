package com.studup.backend.service;

import com.studup.backend.exception.ProfileAlreadyExistsException;
import com.studup.backend.exception.ResourceNotFoundException;
import com.studup.backend.model.dto.request.CreateProprietaireProfileRequest;
import com.studup.backend.model.dto.response.ProprietaireProfileResponse;
import com.studup.backend.model.entity.ProprietaireProfile;
import com.studup.backend.model.entity.User;
import com.studup.backend.repository.ProprietaireProfileRepository;
import com.studup.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProprietaireProfileService {

    private final ProprietaireProfileRepository profileRepository;
    private final UserRepository userRepository;

    public ProprietaireProfileService(ProprietaireProfileRepository profileRepository,
                                      UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProprietaireProfileResponse createProfile(String email, CreateProprietaireProfileRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        if (profileRepository.existsByUserId(user.getId())) {
            throw new ProfileAlreadyExistsException("Un profil propriétaire existe déjà pour ce compte");
        }

        ProprietaireProfile profile = ProprietaireProfile.builder()
                .user(user)
                .phone(request.phone())
                .adresse(request.adresse())
                .ville(request.ville())
                .codePostal(request.codePostal())
                .siret(request.siret())
                .build();

        profile = profileRepository.save(profile);

        return ProprietaireProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public ProprietaireProfileResponse getProfile(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        ProprietaireProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profil propriétaire introuvable"));

        return ProprietaireProfileResponse.from(profile);
    }
}
