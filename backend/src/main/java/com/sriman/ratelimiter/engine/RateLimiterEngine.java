package com.sriman.ratelimiter.engine;

import com.sriman.ratelimiter.domain.RateLimitRule;

public interface RateLimiterEngine {
    RateLimitDecision tryConsume(String clientKey, RateLimitRule rule);
}
