package com.janmeterai.service.janmeteraiservice.model;

public record FaceMatchResponse(boolean matched, double similarity, double threshold, String message) {}
