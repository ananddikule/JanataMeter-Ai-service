package com.janmeterai.service.janmeteraiservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name="janmeter.feed.worker.enabled",havingValue="true")
public class HomeFeedSignalConsumer {
    private static final Set<String> RECENT_EVENTS=Set.of("OPEN","DWELL","SHARE","VIDEO_START","VIDEO_COMPLETE");
    private final ObjectMapper json;private final StringRedisTemplate redis;
    public HomeFeedSignalConsumer(ObjectMapper json,StringRedisTemplate redis){this.json=json;this.redis=redis;}

    @KafkaListener(topics="${janmeter.kafka.feed.engagement-topic}",groupId="janmeter-ai-home-feed-ranking-v1")
    public void engagement(String payload)throws Exception{
        JsonNode event=json.readTree(payload);String eventId=text(event,"eventId"),itemType=text(event,"itemType").toUpperCase(Locale.ROOT),eventType=text(event,"eventType").toUpperCase(Locale.ROOT);
        if(eventId.isBlank()||!first(eventId,Duration.ofDays(8))||"APP".equals(itemType))return;
        long itemId=event.path("itemId").asLong();long userId=event.path("userId").asLong();long duration=event.path("durationMs").asLong();
        double weight=switch(eventType){case "OPEN"->4;case "SHARE"->8;case "VIDEO_COMPLETE"->6;case "HIDE"->-8;case "DWELL"->Math.min(10,duration/10000.0);default->1;};
        String kind=itemType.toLowerCase(Locale.ROOT);increment("feed:trending:"+kind,String.valueOf(itemId),weight,Duration.ofDays(2));
        increment("feed:affinity:"+userId+":"+kind,String.valueOf(itemId),weight,Duration.ofDays(30));
        if(RECENT_EVENTS.contains(eventType)){String recent="feed:recent:"+userId;redis.opsForZSet().add(recent,itemType+":"+itemId,Instant.now().toEpochMilli());redis.expire(recent,Duration.ofDays(14));}
        String seen="feed:seen:"+userId;redis.opsForSet().add(seen,itemType+":"+itemId);redis.expire(seen,Duration.ofDays(7));
    }

    @KafkaListener(topics="${janmeter.kafka.feed.vote-topic}",groupId="janmeter-ai-home-feed-votes-v1")
    public void vote(String payload)throws Exception{
        JsonNode event=json.readTree(payload);String eventId=text(event,"eventId");if(eventId.isBlank()||!first(eventId,Duration.ofDays(35)))return;
        increment("feed:trending:profile",event.path("politicianId").asText(),12,Duration.ofDays(2));
        redis.delete("feed:pool:national");
        if(event.path("assemblyConstituencyId").canConvertToLong()&&!event.path("assemblyConstituencyId").isNull())redis.delete("feed:pool:assembly:"+event.path("assemblyConstituencyId").asLong());
        if(event.path("parliamentConstituencyId").canConvertToLong()&&!event.path("parliamentConstituencyId").isNull())redis.delete("feed:pool:parliament:"+event.path("parliamentConstituencyId").asLong());
    }

    private boolean first(String eventId,Duration ttl){return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("feed:event:processed:"+eventId,"1",ttl));}
    private void increment(String key,String member,double amount,Duration ttl){redis.opsForZSet().incrementScore(key,member,amount);redis.expire(key,ttl);}
    private String text(JsonNode node,String name){return node.path(name).asText("").trim();}
}
