package com.janmeterai.service.janmeteraiservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name="janmeter.feed.worker.enabled",havingValue="true")
public class HomeFeedKafkaTopics {
    @Bean NewTopic feedEngagementTopic(@Value("${janmeter.kafka.feed.engagement-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic feedSessionTopic(@Value("${janmeter.kafka.feed.session-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic feedVoteTopic(@Value("${janmeter.kafka.feed.vote-topic}")String name){return topic(name,1209600000L);}
    @Bean NewTopic feedProfileUpdatedTopic(@Value("${janmeter.kafka.feed.profile-updated-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic feedWorkUpdatedTopic(@Value("${janmeter.kafka.feed.work-updated-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic feedConstituencyAssignedTopic(@Value("${janmeter.kafka.feed.constituency-assigned-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic feedRebuildTopic(@Value("${janmeter.kafka.feed.rebuild-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic feedRetryTopic(@Value("${janmeter.kafka.feed.retry-topic}")String name){return topic(name,604800000L);}
    @Bean NewTopic feedDltTopic(@Value("${janmeter.kafka.feed.dlt-topic}")String name){return topic(name,1209600000L);}

    private NewTopic topic(String name,long retention){return TopicBuilder.name(name).partitions(3).replicas(1)
            .config(TopicConfig.CLEANUP_POLICY_CONFIG,TopicConfig.CLEANUP_POLICY_DELETE)
            .config(TopicConfig.RETENTION_MS_CONFIG,String.valueOf(retention))
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,"1").build();}
}
