package com.janmeterai.service.janmeteraiservice.repository;

import com.janmeterai.service.janmeteraiservice.entity.JanmeterParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JanmeterPartyRepository extends JpaRepository<JanmeterParty, Long> {
}
