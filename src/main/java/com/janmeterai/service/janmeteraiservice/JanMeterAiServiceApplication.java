package com.janmeterai.service.janmeteraiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JanMeterAiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JanMeterAiServiceApplication.class, args);
    }

}
