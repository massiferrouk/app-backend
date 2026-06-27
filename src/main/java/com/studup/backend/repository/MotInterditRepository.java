package com.studup.backend.repository;

import com.studup.backend.model.entity.MotInterdit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MotInterditRepository extends JpaRepository<MotInterdit, UUID> {

    List<MotInterdit> findAll();
}
