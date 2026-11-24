package com.sriman.ratelimiter.config;

import com.sriman.ratelimiter.domain.AlgorithmType;
import com.sriman.ratelimiter.domain.RateLimitRule;
import com.sriman.ratelimiter.repository.RateLimitRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedDefaultRules(RateLimitRuleRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                RateLimitRule demoRule = new RateLimitRule();
                demoRule.setName("demo-api-token-bucket");
                demoRule.setPathPattern("/api/v1/demo/**");
                demoRule.setHttpMethod("GET");
                demoRule.setCapacity(5);
                demoRule.setRefillTokensPerSecond(1.0);
                demoRule.setWindowSeconds(10);
                demoRule.setAlgorithm(AlgorithmType.TOKEN_BUCKET);
                demoRule.setEnabled(true);
                repository.save(demoRule);
            }
        };
    }
}
