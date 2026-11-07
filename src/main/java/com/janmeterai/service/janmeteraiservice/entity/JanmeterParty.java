package com.janmeterai.service.janmeteraiservice.entity;

import jakarta.persistence.*;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_type_id", nullable = false)
    private JanmeterPartyType partyType;

    @Column(name = "abbreviation")
    private String abbreviation;

    @Column(name = "party")
    private String party;

    @Column(name = "created_datetime")
    private LocalDateTime createdDateTime;

    @Column(name = "updated_datetime")
    private LocalDateTime updatedDateTime;
}
