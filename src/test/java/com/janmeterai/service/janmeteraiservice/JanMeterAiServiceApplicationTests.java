package com.janmeterai.service.janmeteraiservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:janmeter-ai-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.enabled=false"
})
class JanMeterAiServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
