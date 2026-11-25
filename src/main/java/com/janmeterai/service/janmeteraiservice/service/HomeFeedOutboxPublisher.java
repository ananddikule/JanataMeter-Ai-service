package com.janmeterai.service.janmeteraiservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="janmeter.feed.worker.enabled",havingValue="true")
public class HomeFeedOutboxPublisher {
    private final JdbcTemplate jdbc;private final KafkaTemplate<String,String> kafka;
    public HomeFeedOutboxPublisher(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka){this.jdbc=jdbc;this.kafka=kafka;}

    @Scheduled(fixedDelayString="${janmeter.feed.outbox-delay:2s}")
    @Transactional
    public void publish(){for(Outbox row:pending()){try{kafka.send(row.topic(),row.messageKey(),row.payload()).get(10,TimeUnit.SECONDS);jdbc.update("UPDATE janmeter_feed_outbox SET published_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=?",row.id());}catch(Exception failure){int attempts=row.attempts()+1;Instant next=Instant.now().plus(Math.min(300,1L<<Math.min(attempts,8)),ChronoUnit.SECONDS);String message=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();jdbc.update("UPDATE janmeter_feed_outbox SET attempt_count=?,next_attempt_at=?,last_error=? WHERE id=?",attempts,Timestamp.from(next),message.substring(0,Math.min(1000,message.length())),row.id());}}}
    private List<Outbox> pending(){return jdbc.query("SELECT id,topic_name,message_key,payload,attempt_count FROM janmeter_feed_outbox WHERE published_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 100",(rs,n)->new Outbox(rs.getLong("id"),rs.getString("topic_name"),rs.getString("message_key"),rs.getString("payload"),rs.getInt("attempt_count")));}
    private record Outbox(long id,String topic,String messageKey,String payload,int attempts){}
}
