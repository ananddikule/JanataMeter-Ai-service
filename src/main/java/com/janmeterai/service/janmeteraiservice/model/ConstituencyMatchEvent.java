package com.janmeterai.service.janmeteraiservice.model;
import java.time.Instant;
public record ConstituencyMatchEvent(String eventId,int eventVersion,Instant occurredAt,Long userId,Long addressVersion,String locality,String villageTownCity,String subDistrict,String district,String state,String pincode,String postOffice,String correlationId) {}
