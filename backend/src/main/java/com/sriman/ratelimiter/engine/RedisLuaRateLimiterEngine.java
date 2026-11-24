package com.sriman.ratelimiter.engine;

import com.sriman.ratelimiter.domain.AlgorithmType;
import com.sriman.ratelimiter.domain.RateLimitRule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed atomic rate limiter executing Lua scripts on Redis.
 */
@Component
public class RedisLuaRateLimiterEngine implements RateLimiterEngine {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final DefaultRedisScript<List> slidingWindowScript;

    public RedisLuaRateLimiterEngine(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        this.tokenBucketScript.setResultType(List.class);

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setLocation(new ClassPathResource("lua/sliding_window.lua"));
        this.slidingWindowScript.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitDecision tryConsume(String clientKey, RateLimitRule rule) {
        long nowMs = Instant.now().toEpochMilli();
        String redisKey = "rl:" + rule.getAlgorithm().name().toLowerCase() + ":" + rule.getId() + ":" + clientKey;

        List<Object> result;
        if (rule.getAlgorithm() == AlgorithmType.SLIDING_WINDOW) {
            long windowMs = Math.max(1000L, rule.getWindowSeconds() * 1000L);
            String member = nowMs + "-" + UUID.randomUUID();
            result = redisTemplate.execute(
                slidingWindowScript,
                List.of(redisKey),
                String.valueOf(rule.getCapacity()),
                String.valueOf(windowMs),
                String.valueOf(nowMs),
                member
            );
        } else {
            result = redisTemplate.execute(
                tokenBucketScript,
                List.of(redisKey),
                String.valueOf(rule.getCapacity()),
                String.valueOf(rule.getRefillTokensPerSecond()),
                String.valueOf(nowMs),
                "1"
            );
        }

        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Unexpected Redis Lua script response: " + result);
        }

        boolean allowed = toLong(result.get(0)) == 1L;
        int remaining = (int) toLong(result.get(1));
        long resetSec = toLong(result.get(2));
        long retryAfter = toLong(result.get(3));

        return new RateLimitDecision(
            allowed,
            rule.getCapacity(),
            Math.max(0, remaining),
            resetSec,
            Math.max(0L, retryAfter),
            "redis-lua",
            rule.getAlgorithm()
        );
    }

    private long toLong(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(val));
    }
}
