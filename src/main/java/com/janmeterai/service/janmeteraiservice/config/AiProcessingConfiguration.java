package com.janmeterai.service.janmeteraiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiProcessingConfiguration {

    @Bean
    public Executor villageCityProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("geographic-processing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
