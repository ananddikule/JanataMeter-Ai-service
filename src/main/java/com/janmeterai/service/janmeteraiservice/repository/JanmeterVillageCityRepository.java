package com.janmeterai.service.janmeteraiservice.repository;

import com.janmeterai.service.janmeteraiservice.entity.JanmeterVillageCity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JanmeterVillageCityRepository extends JpaRepository<JanmeterVillageCity, Long> {

    List<JanmeterVillageCity> findAllByTalukaId(Long talukaId);
}
