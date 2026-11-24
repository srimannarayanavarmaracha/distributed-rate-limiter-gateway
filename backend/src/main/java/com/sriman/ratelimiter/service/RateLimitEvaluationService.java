package com.sriman.ratelimiter.service;

import com.sriman.ratelimiter.domain.RateLimitRule;
import com.sriman.ratelimiter.engine.LocalTokenBucketRateLimiterEngine;
import com.sriman.ratelimiter.engine.RateLimitDecision;
import com.sriman.ratelimiter.engine.RedisLuaRateLimiterEngine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Evaluates incoming requests using distributed Redis Lua execution with automatic
 * failover to in-memory local rate limiting if Redis is disabled or unavailable.
 */
@Service
public class RateLimitEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitEvaluationService.class);

    private final RedisLuaRateLimiterEngine redisEngine;
    private final LocalTokenBucketRateLimiterEngine localEngine;
    private final boolean redisEnabled;
    private final Counter allowedCounter;
    private final Counter blockedCounter;
    private final Counter fallbackCounter;
    private final AtomicLong totalEvaluations = new AtomicLong();
    private final AtomicLong totalBlocked = new AtomicLong();

    public RateLimitEvaluationService(
        RedisLuaRateLimiterEngine redisEngine,
        LocalTokenBucketRateLimiterEngine localEngine,
        MeterRegistry meterRegistry,
        @Value("${ratelimiter.redis.enabled:false}") boolean redisEnabled
    ) {
        this.redisEngine = redisEngine;
        this.localEngine = localEngine;
        this.redisEnabled = redisEnabled;
        this.allowedCounter = meterRegistry.counter("ratelimiter.requests", "outcome", "allowed");
        this.blockedCounter = meterRegistry.counter("ratelimiter.requests", "outcome", "blocked");
        this.fallbackCounter = meterRegistry.counter("ratelimiter.redis.fallback");
    }

    public RateLimitDecision evaluate(String clientKey, RateLimitRule rule) {
        RateLimitDecision decision;
        if (redisEnabled) {
            try {
                decision = redisEngine.tryConsume(clientKey, rule);
            } catch (Exception ex) {
                log.warn("Redis rate limiter unavailable ({}), falling back to local memory", ex.getMessage());
                fallbackCounter.increment();
                decision = localEngine.tryConsume(clientKey, rule);
            }
        } else {
            decision = localEngine.tryConsume(clientKey, rule);
        }

        totalEvaluations.incrementAndGet();
        if (decision.allowed()) {
            allowedCounter.increment();
        } else {
            totalBlocked.incrementAndGet();
            blockedCounter.increment();
        }
        return decision;
    }

    public long getTotalEvaluations() {
        return totalEvaluations.get();
    }

    public long getTotalBlocked() {
        return totalBlocked.get();
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }
}
