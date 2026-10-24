package com.janmeterai.service.janmeteraiservice.repository;

import com.janmeterai.service.janmeteraiservice.entity.JanmeterPartyPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JanmeterPartyPositionRepository extends JpaRepository<JanmeterPartyPosition, Long> {
}
