package com.janmeterai.service.janmeteraiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PoliticianDiscoveryService {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PoliticianDiscoveryService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public List<Map<String, Object>> discover(List<String> positions, int fromYear, int toYear) {
        if (positions == null || positions.isEmpty()) return List.of();
        int safeFrom = Math.max(1947, fromYear);
        int safeTo = Math.max(safeFrom, Math.min(java.time.Year.now().getValue(), toYear));
        String prompt = """
                Find Indian politicians who held at least one of these positions between %d and %d:
                %s

                Return ONLY a JSON object with a candidates array. Do not use markdown. Include only real people
                for whom you can provide at least one credible public source URL. Omit uncertain people and facts.
                Do not invent dates, family, education, wealth, URLs, constituencies, or offices.

                Each candidate must use this complete JanMeter profile shape:
                {"fullName":"","displayName":"","dateOfBirth":null,"dateOfDeath":null,"gender":"",
                "birthplace":"","hometown":"","currentCity":"","about":"","politicalBio":"",
                "email":"","phone":"","website":"","facebookUrl":"","instagramUrl":"",
                "twitterUrl":"","youtubeUrl":"","partyName":"",
                "education":[{"qualification":"","institution":"","fieldOfStudy":"",
                "startYear":null,"endYear":null,"description":""}],
                "family":[{"relationshipType":"","branchLabel":"","name":"","dateOfBirth":null,"description":""}],
                "businesses":[{"businessName":"","industry":"","businessRole":"",
                "ownershipPercentage":null,"startDate":null,"endDate":null,"currentlyActive":false,
                "website":"","description":""}],
                "netWorthHistory":[{"amount":null,"currency":"INR","asOfDate":null,"source":"",
                "movableAssets":null,"immovableAssets":null,"liabilities":null,"declarationUrl":"","notes":""}],
                "career":[{"title":"","partyName":"","organization":"","constituency":"",
                "constituencyType":"","stateCode":"","location":"","startDate":null,"endDate":null,
                "currentlyHolding":false,"description":""}],
                "sources":["https://..."],"confidence":0.0}

                Dates must be ISO yyyy-MM-dd when the complete date is known; otherwise null. Each career entry
                must represent a distinct term. Put a constituency such as Lucknow in constituency, never in title.
                confidence must be 0 to 1. Return at most 12 candidates.
                """.formatted(safeFrom, safeTo, String.join(", ", positions));

        String content = chatClient.prompt().user(prompt).call().content();
        try {
            JsonNode root = objectMapper.readTree(cleanJson(content));
            JsonNode candidates = root.isArray() ? root : root.path("candidates");
            if (!candidates.isArray()) return List.of();
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode candidate : candidates) {
                if (result.size() == 12) break;
                Map<String, Object> value = objectMapper.convertValue(candidate, new TypeReference<LinkedHashMap<String, Object>>() {});
                Object fullName = value.get("fullName");
                Object sources = value.get("sources");
                if (fullName == null || fullName.toString().isBlank() || !(sources instanceof List<?> list) || list.isEmpty()) continue;
                result.add(value);
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI returned an invalid politician candidate response", exception);
        }
    }

    public Map<String, Object> lookup(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        String prompt = """
                Find the Indian politician whose full name is: %s

                Return ONLY a JSON object with a candidate property. Do not use markdown. If this person cannot
                be identified confidently, return {"candidate":null}. Include only facts supported by credible
                public sources. Do not invent dates, family, education, wealth, URLs, constituencies, or offices.

                candidate must use this complete JanMeter profile shape:
                {"fullName":"","displayName":"","dateOfBirth":null,"dateOfDeath":null,"gender":"",
                "birthplace":"","hometown":"","currentCity":"","about":"","politicalBio":"",
                "email":"","phone":"","website":"","facebookUrl":"","instagramUrl":"",
                "twitterUrl":"","youtubeUrl":"","partyName":"",
                "education":[{"qualification":"","institution":"","fieldOfStudy":"",
                "startYear":null,"endYear":null,"description":""}],
                "family":[{"relationshipType":"","branchLabel":"","name":"","dateOfBirth":null,"description":""}],
                "businesses":[{"businessName":"","industry":"","businessRole":"",
                "ownershipPercentage":null,"startDate":null,"endDate":null,"currentlyActive":false,
                "website":"","description":""}],
                "netWorthHistory":[{"amount":null,"currency":"INR","asOfDate":null,"source":"",
                "movableAssets":null,"immovableAssets":null,"liabilities":null,"declarationUrl":"","notes":""}],
                "career":[{"title":"","partyName":"","organization":"","constituency":"",
                "constituencyType":"","stateCode":"","location":"","startDate":null,"endDate":null,
                "currentlyHolding":false,"description":""}],
                "sources":["https://..."],"confidence":0.0}

                Dates must be ISO yyyy-MM-dd when the complete date is known; otherwise null. Each career entry
                must represent a distinct term. Put a constituency such as Lucknow in constituency, never in title.
                """.formatted(fullName.trim());
        String content = chatClient.prompt().user(prompt).call().content();
        try {
            JsonNode root = objectMapper.readTree(cleanJson(content));
            JsonNode candidate = root.path("candidate");
            if (candidate.isMissingNode() || candidate.isNull() || !candidate.isObject()) return null;
            Map<String, Object> value = objectMapper.convertValue(candidate, new TypeReference<LinkedHashMap<String, Object>>() {});
            Object name = value.get("fullName");
            Object sources = value.get("sources");
            if (name == null || name.toString().isBlank() || !(sources instanceof List<?> list) || list.isEmpty()) return null;
            return value;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI returned an invalid politician profile response", exception);
        }
    }

    private String cleanJson(String value) {
        if (value == null) return "{}";
        String cleaned = value.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int objectStart = cleaned.indexOf('{');
        int arrayStart = cleaned.indexOf('[');
        int start = objectStart < 0 ? arrayStart : arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart);
        int end = Math.max(cleaned.lastIndexOf('}'), cleaned.lastIndexOf(']'));
        return start >= 0 && end >= start ? cleaned.substring(start, end + 1) : cleaned;
    }
}
