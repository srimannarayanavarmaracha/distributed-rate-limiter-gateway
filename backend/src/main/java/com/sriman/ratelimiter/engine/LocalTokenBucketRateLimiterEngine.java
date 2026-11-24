package com.sriman.ratelimiter.engine;

import com.sriman.ratelimiter.domain.AlgorithmType;
import com.sriman.ratelimiter.domain.RateLimitRule;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory rate limiter engine supporting Token Bucket and Sliding Window algorithms.
 * Used for deterministic testing and automatic graceful degradation if Redis is unreachable.
 */
@Component
public class LocalTokenBucketRateLimiterEngine implements RateLimiterEngine {

    private static final class BucketState {
        double tokens;
        long lastRefillNanos;

        BucketState(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }

    private final Map<String, BucketState> tokenBuckets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> slidingWindows = new ConcurrentHashMap<>();
    private final Clock clock;

    public LocalTokenBucketRateLimiterEngine() {
        this(Clock.systemUTC());
    }

    public LocalTokenBucketRateLimiterEngine(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryConsume(String clientKey, RateLimitRule rule) {
        String scopedKey = rule.getId() + ":" + rule.getAlgorithm().name() + ":" + clientKey;
        if (rule.getAlgorithm() == AlgorithmType.SLIDING_WINDOW) {
            return trySlidingWindow(scopedKey, rule);
        }
        return tryTokenBucket(scopedKey, rule);
    }

    private RateLimitDecision tryTokenBucket(String key, RateLimitRule rule) {
        long nowNanos = System.nanoTime();
        long nowSeconds = clock.instant().getEpochSecond();
        BucketState state = tokenBuckets.computeIfAbsent(
            key, k -> new BucketState(rule.getCapacity(), nowNanos)
        );

        synchronized (state) {
            double elapsedSeconds = Math.max(0.0, (nowNanos - state.lastRefillNanos) / 1_000_000_000.0);
            double added = elapsedSeconds * rule.getRefillTokensPerSecond();
            if (added > 0) {
                state.tokens = Math.min(rule.getCapacity(), state.tokens + added);
                state.lastRefillNanos = nowNanos;
            }

            if (state.tokens >= 1.0) {
                state.tokens -= 1.0;
                int remaining = (int) Math.floor(state.tokens);
                return new RateLimitDecision(
                    true,
                    rule.getCapacity(),
                    remaining,
                    nowSeconds + 1,
                    0,
                    "local-memory",
                    AlgorithmType.TOKEN_BUCKET
                );
            }

            double deficit = 1.0 - state.tokens;
            long retryAfter = rule.getRefillTokensPerSecond() > 0
                ? (long) Math.ceil(deficit / rule.getRefillTokensPerSecond())
                : 1L;
            return new RateLimitDecision(
                false,
                rule.getCapacity(),
                0,
                nowSeconds + retryAfter,
                retryAfter,
                "local-memory",
                AlgorithmType.TOKEN_BUCKET
            );
        }
    }

    private RateLimitDecision trySlidingWindow(String key, RateLimitRule rule) {
        long nowMillis = clock.millis();
        long windowMillis = Math.max(1000L, rule.getWindowSeconds() * 1000L);
        long windowStart = nowMillis - windowMillis;

        Deque<Long> timestamps = slidingWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            int limit = rule.getCapacity();
            if (timestamps.size() < limit) {
                timestamps.addLast(nowMillis);
                int remaining = limit - timestamps.size();
                long resetSec = (nowMillis + windowMillis + 999) / 1000;
                return new RateLimitDecision(
                    true,
                    limit,
                    remaining,
                    resetSec,
                    0,
                    "local-memory",
                    AlgorithmType.SLIDING_WINDOW
                );
            }

            long oldest = timestamps.peekFirst() != null ? timestamps.peekFirst() : nowMillis;
            long waitMillis = Math.max(1000L, (oldest + windowMillis) - nowMillis);
            long retryAfter = (waitMillis + 999) / 1000;
            long resetSec = (nowMillis + waitMillis + 999) / 1000;
            return new RateLimitDecision(
                false,
                limit,
                0,
                resetSec,
                retryAfter,
                "local-memory",
                AlgorithmType.SLIDING_WINDOW
            );
        }
    }

    public void clear() {
        tokenBuckets.clear();
        slidingWindows.clear();
    }
}
