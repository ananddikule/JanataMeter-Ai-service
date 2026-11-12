package com.janmeterai.service.janmeteraiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.janmeterai.service.janmeteraiservice.model.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name="janmeter.kafka.enabled",havingValue="true")
public class ConstituencyKafkaConsumer {
    private static final Logger log=LoggerFactory.getLogger(ConstituencyKafkaConsumer.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();private final ConstituencyAddressMatcher matcher;
    private final KafkaTemplate<String,String> kafka;private final String resultTopic;private final String dltTopic;
    public ConstituencyKafkaConsumer(ConstituencyAddressMatcher matcher,KafkaTemplate<String,String> kafka,@Value("${janmeter.kafka.constituency.result-topic}")String resultTopic,@Value("${janmeter.kafka.constituency.dlt-topic}")String dltTopic){this.matcher=matcher;this.kafka=kafka;this.resultTopic=resultTopic;this.dltTopic=dltTopic;}
    @KafkaListener(topics="${janmeter.kafka.constituency.request-topic}",groupId="janmeter-ai-address-matcher-v1",concurrency="${JANMETER_AI_CONSTITUENCY_CONCURRENCY:1}")
    public void consume(String payload){
        ConstituencyMatchEvent request=null;
        try{request=json.readValue(payload,ConstituencyMatchEvent.class);var result=matcher.match(request);kafka.send(resultTopic,String.valueOf(request.userId()),json.writeValueAsString(result));}
        catch(Exception exception){
            log.error("Constituency address matching failed",exception);
            try{if(request!=null){var failed=new ConstituencyMatchResultEvent(UUID.randomUUID().toString(),request.eventId(),Instant.now(),request.userId(),"FAILED",null,null,0.0,"ERROR",safe(exception.getMessage()),request.correlationId());kafka.send(resultTopic,String.valueOf(request.userId()),json.writeValueAsString(failed));}kafka.send(dltTopic,request==null?"unknown":String.valueOf(request.userId()),payload);}catch(Exception publishError){log.error("Could not publish failed constituency event",publishError);}
        }
    }
    private String safe(String value){if(value==null||value.isBlank())return "Address matching failed in the AI service.";return value.substring(0,Math.min(400,value.length()));}
}
