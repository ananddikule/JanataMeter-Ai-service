package com.janmeterai.service.janmeteraiservice.repository;

import com.janmeterai.service.janmeteraiservice.entity.JanmeterTaluka;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JanmeterTalukaRepository extends JpaRepository<JanmeterTaluka, Long> {

    List<JanmeterTaluka> findAllByDistrictId(Long districtId);
}
