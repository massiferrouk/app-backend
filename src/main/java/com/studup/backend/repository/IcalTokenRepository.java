package com.studup.backend.repository;

import com.studup.backend.model.entity.IcalToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IcalTokenRepository extends JpaRepository<IcalToken, UUID> {

    Optional<IcalToken> findByUserId(UUID userId);

    Optional<IcalToken> findByTokenAndIsActiveTrue(String token);
}
