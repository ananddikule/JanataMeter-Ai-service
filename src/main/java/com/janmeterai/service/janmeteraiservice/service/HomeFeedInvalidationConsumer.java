package com.janmeterai.service.janmeteraiservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name="janmeter.feed.worker.enabled",havingValue="true")
public class HomeFeedInvalidationConsumer {
    private final HomeFeedMaintenanceScheduler maintenance;private final ObjectMapper json;private final StringRedisTemplate redis;
    public HomeFeedInvalidationConsumer(HomeFeedMaintenanceScheduler maintenance,ObjectMapper json,StringRedisTemplate redis){this.maintenance=maintenance;this.json=json;this.redis=redis;}
    @KafkaListener(topics={"${janmeter.kafka.feed.profile-updated-topic}","${janmeter.kafka.feed.work-updated-topic}","${janmeter.kafka.feed.rebuild-topic}"},groupId="janmeter-ai-feed-invalidation-v1")
    public void contentChanged(String ignored){maintenance.invalidateFeedCaches();}
    @KafkaListener(topics="${janmeter.kafka.feed.constituency-assigned-topic}",groupId="janmeter-ai-feed-constituency-v1")
    public void constituencyChanged(String payload){try{JsonNode event=json.readTree(payload);long userId=event.path("userId").asLong();if(userId>0)maintenance.deletePattern("feed:personalized:"+userId+":*");maintenance.invalidateFeedPools();}catch(Exception ignored){maintenance.invalidateFeedCaches();}}
}
