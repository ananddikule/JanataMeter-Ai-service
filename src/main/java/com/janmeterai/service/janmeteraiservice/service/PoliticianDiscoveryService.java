package com.janmeterai.service.janmeteraiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class PoliticianDiscoveryService {
    private final PoliticianResearchService researchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PoliticianDiscoveryService(
            PoliticianResearchService researchService
    ) {
        this.researchService = researchService;
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

        PoliticianResearchProvider.ResearchResult research = researchService.research(prompt, String.join(" ", positions) + " " + safeFrom + " " + safeTo);
        String content = research.text();
        try {
            JsonNode root = objectMapper.readTree(cleanJson(content));
            JsonNode candidates = root.isArray() ? root : root.path("candidates");
            if (!candidates.isArray()) return List.of();
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode candidate : candidates) {
                if (result.size() == 12) break;
                Map<String, Object> value = objectMapper.convertValue(candidate, new TypeReference<LinkedHashMap<String, Object>>() {});
                addResearchSources(value, research.sourceUrls());
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
                Perform a comprehensive profile lookup rather than returning a short summary. Populate every
                supported field for which a credible fact is available, especially birthplace, hometown, party,
                complete education, family relationships, business interests, declared net-worth history,
                contact/social links, and the politician's complete term-by-term career timeline. Preserve empty
                strings or arrays only when that information cannot be supported by a credible public source.

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
                Include every known elected, parliamentary, government, party, committee, and opposition office
                as a separate career entry, with its party, organization, constituency, location and tenure.
                For education, research school/secondary, college, university and professional study separately;
                retain a supported entry even when a year is unknown. For businesses, include only documented
                companies, ownership, directorships or operating roles, and do not infer a relative's business.
                For netWorthHistory, return every available election affidavit newest first, with exact numeric INR
                values, ISO declaration date, direct declaration URL, election/year source and calculation notes.
                amount means assets minus liabilities; movableAssets and immovableAssets remain separate values.
                """.formatted(fullName.trim());
        PoliticianResearchProvider.ResearchResult research = researchService.research(prompt, fullName.trim());
        String content = research.text();
        try {
            JsonNode root = objectMapper.readTree(cleanJson(content));
            JsonNode candidate = root.path("candidate");
            if (candidate.isMissingNode() || candidate.isNull() || !candidate.isObject()) return null;
            Map<String, Object> value = objectMapper.convertValue(candidate, new TypeReference<LinkedHashMap<String, Object>>() {});
            addResearchSources(value, research.sourceUrls());
            Object name = value.get("fullName");
            Object sources = value.get("sources");
            if (name == null || name.toString().isBlank() || !(sources instanceof List<?> list) || list.isEmpty()) return null;
            return value;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI returned an invalid politician profile response", exception);
        }
    }

    public Map<String, Object> lookupSection(String fullName, String requestedSection) {
        if (fullName == null || fullName.isBlank()) return null;
        String section = requestedSection == null ? "" : requestedSection.trim().toUpperCase();
        String sectionInstructions = switch (section) {
            case "PERSONAL" -> """
                    Return personal biography and contact facts using exactly these supported fields:
                    {"fullName":"","displayName":"","dateOfBirth":null,"dateOfDeath":null,
                    "gender":"","birthplace":"","hometown":"","currentCity":"","about":"",
                    "email":"","phone":"","website":"","facebookUrl":"","instagramUrl":"",
                    "twitterUrl":"","youtubeUrl":"","partyName":"","sources":["https://..."]}
                    """;
            case "EDUCATION" -> """
                    Research the complete education history, not only the highest qualification. Check official
                    parliamentary or government biographies, election affidavits, party biographies, educational
                    institution records and credible interviews. Include school/secondary education, college,
                    university, professional courses and honorary study only when publicly documented. A missing
                    start or end year must not cause an otherwise supported education entry to be omitted; use null.
                    Explain useful evidence or uncertainty in description. Return exactly these supported fields:
                    {"fullName":"","education":[{"qualification":"","institution":"",
                    "fieldOfStudy":"","startYear":null,"endYear":null,"description":""}],
                    "sources":["https://..."]}
                    """;
            case "FAMILY" -> """
                    Return publicly documented close family relationships using exactly these supported fields:
                    {"fullName":"","family":[{"relationshipType":"SPOUSE|CHILD|MOTHER|FATHER|SIBLING|OTHER",
                    "branchLabel":"","name":"","dateOfBirth":null,"description":""}],
                    "sources":["https://..."]}
                    """;
            case "BUSINESS" -> """
                    Research documented business interests separately from political employment. Check election
                    affidavits, corporate filings, official biographies and credible financial reporting. Include
                    every supported company, partnership, directorship, ownership interest and active business role.
                    Do not infer ownership from a relative's business or from an unsupported media claim. A missing
                    date or ownership percentage must not cause a supported business entry to be omitted; use null.
                    Return exactly these supported fields:
                    {"fullName":"","businesses":[{"businessName":"","industry":"","businessRole":"",
                    "ownershipPercentage":null,"startDate":null,"endDate":null,"currentlyActive":false,
                    "website":"","description":""}],"sources":["https://..."]}
                    """;
            case "NET_WORTH" -> """
                    Research every available historical election affidavit or official asset declaration for this
                    politician, not merely a current estimated net worth. Prefer MyNeta/ADR and Election Commission
                    affidavit pages. For each declaration, return the exact numeric rupee values reported for total
                    assets, movable assets, immovable assets and liabilities. Set amount to total assets minus
                    liabilities (net worth); do not return crore/lakh-formatted strings and do not estimate missing
                    values. Include the election and year in source, the direct evidence URL in declarationUrl and
                    a concise explanation of the calculation in notes. Return newest declaration first.
                    Return exactly these supported fields:
                    {"fullName":"","netWorthHistory":[{"amount":null,"currency":"INR",
                    "asOfDate":null,"source":"","movableAssets":null,"immovableAssets":null,
                    "liabilities":null,"declarationUrl":"","notes":""}],"sources":["https://..."]}
                    """;
            case "POLITICAL" -> """
                    Return political biography and the complete term-by-term political career using exactly these fields:
                    {"fullName":"","politicalBio":"","partyName":"","career":[{"title":"",
                    "partyName":"","organization":"","constituency":"","constituencyType":"",
                    "stateCode":"","location":"","startDate":null,"endDate":null,
                    "currentlyHolding":false,"description":""}],"sources":["https://..."]}
                    Include every known elected, parliamentary, government, party, committee and opposition office
                    as a separate career entry. Put the seat name in constituency, never in title.
                    """;
            default -> throw new IllegalArgumentException("Unsupported politician profile section: " + requestedSection);
        };
        String prompt = """
                Find the Indian politician whose full name is: %s

                Fetch ONLY the %s section of the JanMeter politician profile. Return ONLY a JSON object with a
                profile property; do not use markdown. If the person cannot be identified confidently, return
                {"profile":null}. Include only facts supported by credible public sources. Do not invent facts.
                Use ISO yyyy-MM-dd only when a complete date is known; otherwise use null. Use empty arrays when
                the requested section has no reliably documented entries.

                %s
                """.formatted(fullName.trim(), section, sectionInstructions);
        PoliticianResearchProvider.ResearchResult research = researchService.research(prompt, fullName.trim() + " " + section);
        String content = research.text();
        try {
            JsonNode root = objectMapper.readTree(cleanJson(content));
            JsonNode profile = root.path("profile");
            if (profile.isMissingNode() || profile.isNull() || !profile.isObject()) return null;
            Map<String, Object> value = objectMapper.convertValue(profile, new TypeReference<LinkedHashMap<String, Object>>() {});
            addResearchSources(value, research.sourceUrls());
            Object name = value.get("fullName");
            Object sources = value.get("sources");
            if (name == null || name.toString().isBlank() || !(sources instanceof List<?> list) || list.isEmpty()) return null;
            return value;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI returned an invalid " + section.toLowerCase() + " politician profile response", exception);
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

    private void addResearchSources(Map<String, Object> value, List<String> researchedSources) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        Object existing = value.get("sources");
        if (existing instanceof List<?> list) list.stream().filter(java.util.Objects::nonNull).map(Object::toString).filter(url -> !url.isBlank()).forEach(merged::add);
        if (researchedSources != null) merged.addAll(researchedSources);
        value.put("sources", List.copyOf(merged));
    }

}
