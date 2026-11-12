package com.janmeterai.service.janmeteraiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpenAiWebResearchService implements PoliticianResearchProvider {
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final Duration timeout;
    private final Duration cacheTtl;
    private final int maxOutputTokens;
    private final String responsesUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedResearch> cache = new ConcurrentHashMap<>();

    public OpenAiWebResearchService(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${janmeter.ai.politician.research-model:gpt-6-astra}") String model,
            @Value("${janmeter.ai.politician.research-reasoning-effort:low}") String reasoningEffort,
            @Value("${janmeter.ai.politician.timeout:PT9M}") Duration timeout,
            @Value("${janmeter.ai.politician.research-cache-ttl:PT24H}") Duration cacheTtl,
            @Value("${janmeter.ai.politician.research-max-output-tokens:10000}") int maxOutputTokens,
            @Value("${janmeter.ai.openai.responses-url:https://api.openai.com/v1/responses}") String responsesUrl
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.timeout = timeout;
        this.cacheTtl = cacheTtl;
        this.maxOutputTokens = Math.max(1000, maxOutputTokens);
        this.responsesUrl = responsesUrl;
    }

    @Override
    public ResearchResult research(String prompt, String subject) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured for politician web research");
        }
        String cacheKey = cacheKey(model + "\n" + reasoningEffort + "\n" + prompt);
        CachedResearch cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.result();
        if (cached != null) cache.remove(cacheKey, cached);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(timeout);
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        Map<String, Object> request = Map.of(
                "model", model,
                "reasoning", Map.of("effort", reasoningEffort),
                "tools", List.of(Map.of("type", "web_search", "search_context_size", "medium")),
                "tool_choice", "required",
                "include", List.of("web_search_call.action.sources"),
                "max_output_tokens", maxOutputTokens,
                "store", false,
                "input", prompt
        );
        String raw = client.post().uri(responsesUrl).body(request).retrieve().body(String.class);
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "{}" : raw);
            List<String> textParts = new ArrayList<>();
            LinkedHashSet<String> sources = new LinkedHashSet<>();
            for (JsonNode output : root.path("output")) {
                collectUrls(output, sources);
                if (!"message".equals(output.path("type").asText())) continue;
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) textParts.add(content.path("text").asText());
                    collectUrls(content, sources);
                }
            }
            String text = String.join("\n", textParts).trim();
            if (text.isBlank()) throw new IllegalStateException("OpenAI web research returned no text output");
            ResearchResult result = new ResearchResult(text, List.copyOf(sources));
            cache.put(cacheKey, new CachedResearch(result, Instant.now().plus(cacheTtl)));
            return result;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI returned an invalid web research response", exception);
        }
    }

    private String cacheKey(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create politician research cache key", exception);
        }
    }

    private void collectUrls(JsonNode node, LinkedHashSet<String> sources) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("url".equals(entry.getKey()) && entry.getValue().isTextual()) {
                    String value = entry.getValue().asText().trim();
                    if (value.startsWith("http://") || value.startsWith("https://")) sources.add(value);
                } else collectUrls(entry.getValue(), sources);
            });
        } else if (node.isArray()) node.forEach(value -> collectUrls(value, sources));
    }

    private record CachedResearch(ResearchResult result, Instant expiresAt) {}
}
