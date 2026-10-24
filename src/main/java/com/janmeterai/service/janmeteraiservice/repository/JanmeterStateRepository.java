package com.janmeterai.service.janmeteraiservice.repository;

import com.janmeterai.service.janmeteraiservice.entity.JanmeterState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JanmeterStateRepository extends JpaRepository<JanmeterState, Long> {

    java.util.List<JanmeterState> findAllByOrderByIdAsc();
}
