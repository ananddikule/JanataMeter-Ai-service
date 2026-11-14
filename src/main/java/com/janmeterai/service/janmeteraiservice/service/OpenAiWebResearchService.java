package com.janmeterai.service.janmeteraiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedResearch> cache = new ConcurrentHashMap<>();

    public OpenAiWebResearchService(
            StringRedisTemplate redis,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${janmeter.ai.politician.research-model:gpt-6-luna}") String model,
            @Value("${janmeter.ai.politician.research-reasoning-effort:none}") String reasoningEffort,
            @Value("${janmeter.ai.politician.timeout:PT9M}") Duration timeout,
            @Value("${janmeter.ai.politician.research-cache-ttl:P7D}") Duration cacheTtl,
            @Value("${janmeter.ai.politician.research-max-output-tokens:5000}") int maxOutputTokens,
            @Value("${janmeter.ai.openai.responses-url:https://api.openai.com/v1/responses}") String responsesUrl
    ) {
        this.redis = redis;
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
        CachedResearch sharedCached = readSharedCache(cacheKey);
        if (sharedCached != null) {
            cache.put(cacheKey, sharedCached);
            return sharedCached.result();
        }
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
                "tools", List.of(Map.of("type", "web_search", "search_context_size", "low")),
                "tool_choice", "required",
                "include", List.of("web_search_call.action.sources"),
                "max_output_tokens", outputTokenLimit(prompt),
                "max_tool_calls", webSearchLimit(prompt),
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
            CachedResearch value = new CachedResearch(result, Instant.now().plus(cacheTtl));
            cache.put(cacheKey, value);
            writeSharedCache(cacheKey, value);
            return result;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("OpenAI returned an invalid web research response", exception);
        }
    }

    private int outputTokenLimit(String prompt) {
        String value = prompt == null ? "" : prompt.toUpperCase();
        int sectionLimit = value.contains("PERSONAL SECTION") ? 1600
                : value.contains("EDUCATION SECTION") ? 2000
                : value.contains("FAMILY SECTION") ? 1600
                : value.contains("BUSINESS SECTION") ? 2000
                : value.contains("NET_WORTH SECTION") ? 2400
                : value.contains("POLITICAL SECTION") ? 3200
                : 5000;
        return Math.max(1000, Math.min(maxOutputTokens, sectionLimit));
    }

    private int webSearchLimit(String prompt) {
        String value = prompt == null ? "" : prompt.toUpperCase();
        if (value.contains("PERSONAL SECTION") || value.contains("FAMILY SECTION")) return 5;
        if (value.contains("EDUCATION SECTION") || value.contains("BUSINESS SECTION")) return 7;
        if (value.contains("NET_WORTH SECTION") || value.contains("POLITICAL SECTION")) return 10;
        return 10;
    }

    private CachedResearch readSharedCache(String cacheKey) {
        try {
            String raw = redis.opsForValue().get("janmeter:politician-research:" + cacheKey);
            if (raw == null || raw.isBlank()) return null;
            JsonNode value = objectMapper.readTree(raw);
            Instant expiresAt = Instant.parse(value.path("expiresAt").asText());
            if (!expiresAt.isAfter(Instant.now())) return null;
            List<String> urls = new ArrayList<>();
            value.path("sourceUrls").forEach(url -> urls.add(url.asText()));
            return new CachedResearch(new ResearchResult(value.path("text").asText(), List.copyOf(urls)), expiresAt);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeSharedCache(String cacheKey, CachedResearch value) {
        try {
            String raw = objectMapper.writeValueAsString(Map.of(
                    "text", value.result().text(),
                    "sourceUrls", value.result().sourceUrls(),
                    "expiresAt", value.expiresAt().toString()));
            redis.opsForValue().set("janmeter:politician-research:" + cacheKey, raw, cacheTtl);
        } catch (Exception ignored) {
            // Redis is an optimization only; an unavailable cache must not fail profile research.
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
