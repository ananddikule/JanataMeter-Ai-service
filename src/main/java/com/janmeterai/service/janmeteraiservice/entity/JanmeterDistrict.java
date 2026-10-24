package com.janmeterai.service.janmeteraiservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "janmeter_district")
@Getter
@Setter
@NoArgsConstructor
public class JanmeterDistrict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "districtname")
    private String districtName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private JanmeterState state;

    @Column(name = "createddatetime")
    private LocalDateTime createdDateTime;

    @Column(name = "updateddatetime")
    private LocalDateTime updatedDateTime;
}
