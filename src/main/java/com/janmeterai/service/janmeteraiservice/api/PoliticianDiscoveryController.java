package com.janmeterai.service.janmeteraiservice.api;

import com.janmeterai.service.janmeteraiservice.service.PoliticianDiscoveryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/politician-candidates")
public class PoliticianDiscoveryController {
    private final PoliticianDiscoveryService service;

    public PoliticianDiscoveryController(PoliticianDiscoveryService service) {
        this.service = service;
    }

    @PostMapping("/discover")
    public DiscoverResponse discover(@RequestBody DiscoverRequest request) {
        return new DiscoverResponse(service.discover(request.positions(), request.fromYear(), request.toYear()));
    }

    @PostMapping("/lookup")
    public LookupResponse lookup(@RequestBody LookupRequest request) {
        return new LookupResponse(service.lookup(request.fullName()));
    }

    @PostMapping("/lookup-section")
    public LookupSectionResponse lookupSection(@RequestBody LookupSectionRequest request) {
        return new LookupSectionResponse(request.section(), service.lookupSection(request.fullName(), request.section()));
    }

    public record DiscoverRequest(List<String> positions, int fromYear, int toYear) {}
    public record DiscoverResponse(List<Map<String, Object>> candidates) {}
    public record LookupRequest(String fullName) {}
    public record LookupResponse(Map<String, Object> candidate) {}
    public record LookupSectionRequest(String fullName, String section) {}
    public record LookupSectionResponse(String section, Map<String, Object> profile) {}
}
