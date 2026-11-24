package com.sriman.ratelimiter.web.dto;

import com.sriman.ratelimiter.domain.AlgorithmType;
import com.sriman.ratelimiter.domain.RateLimitRule;
import java.time.Instant;

public record RuleResponse(
    Long id,
    String name,
    String pathPattern,
    String httpMethod,
    int capacity,
    double refillTokensPerSecond,
    int windowSeconds,
    AlgorithmType algorithm,
    boolean enabled,
    Instant updatedAt
) {
    public static RuleResponse from(RateLimitRule rule) {
        return new RuleResponse(
            rule.getId(),
            rule.getName(),
            rule.getPathPattern(),
            rule.getHttpMethod(),
            rule.getCapacity(),
            rule.getRefillTokensPerSecond(),
            rule.getWindowSeconds(),
            rule.getAlgorithm(),
            rule.isEnabled(),
            rule.getUpdatedAt()
        );
    }
}
