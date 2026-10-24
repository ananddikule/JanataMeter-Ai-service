package com.janmeterai.service.janmeteraiservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "janmeter_party_positions")
@Getter
@Setter
@NoArgsConstructor
public class JanmeterPartyPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "createddatetime")
    private LocalDateTime createdDateTime;

    @Column(name = "updateddatetime")
    private LocalDateTime updatedDateTime;
}
