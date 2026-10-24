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
@Table(name = "janmeter_ward")
@Getter
@Setter
@NoArgsConstructor
public class JanmeterWard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "wardnumber")
    private Integer wardNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "village_city_id")
    private JanmeterVillageCity villageCity;

    @Column(name = "createddatetime")
    private LocalDateTime createdDateTime;

    @Column(name = "updateddatetime")
    private LocalDateTime updatedDateTime;
}
