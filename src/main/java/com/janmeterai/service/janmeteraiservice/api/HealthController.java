package com.janmeterai.service.janmeteraiservice.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping("/check")
    public Map<String, String> check() {
        return Map.of("status", "UP", "service", "janmeter-ai-service");
    }
}
