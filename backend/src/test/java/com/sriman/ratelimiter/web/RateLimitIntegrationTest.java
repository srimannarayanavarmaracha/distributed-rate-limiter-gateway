package com.sriman.ratelimiter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sriman.ratelimiter.engine.LocalTokenBucketRateLimiterEngine;
import com.sriman.ratelimiter.filter.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class RateLimitIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private LocalTokenBucketRateLimiterEngine localEngine;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        localEngine.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(rateLimitFilter)
            .build();
    }

    @Test
    void demoEndpointShouldEnforceSeededLimitAndReturn429WithHeaders() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/demo/resource").header("X-Client-Id", "integration-user"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"));
        }

        mockMvc.perform(get("/api/v1/demo/resource").header("X-Client-Id", "integration-user"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void shouldCreateAndListRateLimitRule() throws Exception {
        String payload = """
            {
              "name": "payments-sliding-window",
              "pathPattern": "/api/v1/payments/**",
              "httpMethod": "POST",
              "capacity": 20,
              "refillTokensPerSecond": 2.0,
              "windowSeconds": 60,
              "algorithm": "SLIDING_WINDOW",
              "enabled": true
            }
            """;

        mockMvc.perform(post("/api/v1/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("payments-sliding-window"))
            .andExpect(jsonPath("$.algorithm").value("SLIDING_WINDOW"));

        mockMvc.perform(get("/api/v1/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }
}
