package com.janmeterai.service.janmeteraiservice.repository;

import com.janmeterai.service.janmeteraiservice.entity.JanmeterDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JanmeterDistrictRepository extends JpaRepository<JanmeterDistrict, Long> {

    List<JanmeterDistrict> findAllByStateId(Long stateId);
}
