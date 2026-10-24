package com.janmeterai.service.janmeteraiservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-api-key")
class JanMeterAiServiceApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        Integer stateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM janmeter_state", Integer.class);
        assertThat(stateCount).isEqualTo(36);
    }

}
