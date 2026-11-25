package com.janmeterai.service.janmeteraiservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name="janmeter.feed.worker.enabled",havingValue="true")
public class HomeFeedMaintenanceScheduler {
    private final StringRedisTemplate redis;private final JdbcTemplate jdbc;
    public HomeFeedMaintenanceScheduler(StringRedisTemplate redis,JdbcTemplate jdbc){this.redis=redis;this.jdbc=jdbc;}

    @Scheduled(initialDelayString="${janmeter.feed.rebuild-initial-delay:30s}",fixedDelayString="${janmeter.feed.rebuild-delay:10m}")
    public void invalidateFeedCaches(){withLock("feed:lock:ai-rebuild",Duration.ofMinutes(2),()->{invalidateFeedPools();deletePattern("feed:personalized:*");});}
    void invalidateFeedPools(){deletePattern("feed:pool:*");}

    @Scheduled(initialDelayString="5m",fixedDelayString="${janmeter.feed.ranking-decay-delay:6h}")
    public void decayRankingSignals(){withLock("feed:lock:ai-ranking-decay",Duration.ofMinutes(20),()->{decay("feed:affinity:*",.82);decay("feed:trending:*",.72);});}

    @Scheduled(initialDelayString="15m",fixedDelayString="${janmeter.feed.cleanup-delay:24h}")
    public void cleanupPublishedOutbox(){withLock("feed:lock:ai-outbox-cleanup",Duration.ofHours(1),()->jdbc.update("DELETE FROM janmeter_feed_outbox WHERE published_at IS NOT NULL AND published_at < CURRENT_TIMESTAMP - INTERVAL '7 days'"));}

    void deletePattern(String pattern){try(Cursor<String> keys=redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())){while(keys.hasNext())redis.delete(keys.next());}}
    private void decay(String pattern,double factor){try(Cursor<String> keys=redis.scan(ScanOptions.scanOptions().match(pattern).count(200).build())){while(keys.hasNext()){String key=keys.next();var rows=redis.opsForZSet().reverseRangeWithScores(key,0,1999);if(rows==null)continue;for(ZSetOperations.TypedTuple<String> row:rows){if(row.getValue()==null||row.getScore()==null)continue;double next=row.getScore()*factor;if(Math.abs(next)<.2)redis.opsForZSet().remove(key,row.getValue());else redis.opsForZSet().add(key,row.getValue(),next);}}}}
    private void withLock(String key,Duration ttl,Runnable task){boolean acquired=false;try{acquired=Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,"1",ttl));if(acquired)task.run();}finally{if(acquired)redis.delete(key);}}
}
