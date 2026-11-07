package com.janmeterai.service.janmeteraiservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "party_types")
@Getter
@Setter
@NoArgsConstructor
public class JanmeterPartyType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_datetime", updatable = false)
    private LocalDateTime createdDateTime;

    @Column(name = "updated_datetime")
    private LocalDateTime updatedDateTime;

    @PrePersist
    protected void setCreationTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        createdDateTime = now;
        updatedDateTime = now;
    }

    @PreUpdate
    protected void setUpdatedTimestamp() {
        updatedDateTime = LocalDateTime.now();
    }
}
