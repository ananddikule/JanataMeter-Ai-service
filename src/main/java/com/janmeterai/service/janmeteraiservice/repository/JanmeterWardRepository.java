package com.janmeterai.service.janmeteraiservice.repository;

import com.janmeterai.service.janmeteraiservice.entity.JanmeterWard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JanmeterWardRepository extends JpaRepository<JanmeterWard, Long> {

    List<JanmeterWard> findAllByVillageCityId(Long villageCityId);
}
