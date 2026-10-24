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
@Table(name = "janmeter_taluka")
@Getter
@Setter
@NoArgsConstructor
public class JanmeterTaluka {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "talukaname")
    private String talukaName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private JanmeterDistrict district;

    @Column(name = "createddatetime")
    private LocalDateTime createdDateTime;

    @Column(name = "updateddatetime")
    private LocalDateTime updatedDateTime;
}
