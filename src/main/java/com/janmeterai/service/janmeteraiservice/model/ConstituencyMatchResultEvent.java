package com.janmeterai.service.janmeteraiservice.model;
import java.time.Instant;
public record ConstituencyMatchResultEvent(String eventId,String requestEventId,Instant occurredAt,Long userId,String status,Long assemblyConstituencyId,Long parliamentConstituencyId,Double confidence,String matchedBy,String message,String correlationId) {}
