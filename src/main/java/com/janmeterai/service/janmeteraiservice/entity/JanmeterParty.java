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
@Table(name = "janmeter_parties")
@Getter
@Setter
@NoArgsConstructor
public class JanmeterParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_type")
    private String partyType;

    @Column(name = "abbreviation")
    private String abbreviation;

    @Column(name = "party")
    private String party;

    @Column(name = "created_datetime")
    private LocalDateTime createdDateTime;

    @Column(name = "updated_datetime")
    private LocalDateTime updatedDateTime;
}
