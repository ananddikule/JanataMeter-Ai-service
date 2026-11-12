package com.janmeterai.service.janmeteraiservice.config;

import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration
@ConditionalOnProperty(name="janmeter.kafka.enabled",havingValue="true")
public class ConstituencyKafkaTopics {
    @Bean NewTopic constituencyRequestTopic(@Value("${janmeter.kafka.constituency.request-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic constituencyResultTopic(@Value("${janmeter.kafka.constituency.result-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic constituencyRetryTopic(@Value("${janmeter.kafka.constituency.retry-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic constituencyDltTopic(@Value("${janmeter.kafka.constituency.dlt-topic}")String name){return topic(name,1209600000L);}
    private NewTopic topic(String name,long retention){return TopicBuilder.name(name).partitions(3).replicas(1).config(TopicConfig.CLEANUP_POLICY_CONFIG,TopicConfig.CLEANUP_POLICY_DELETE).config(TopicConfig.RETENTION_MS_CONFIG,String.valueOf(retention)).config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,"1").build();}
}
