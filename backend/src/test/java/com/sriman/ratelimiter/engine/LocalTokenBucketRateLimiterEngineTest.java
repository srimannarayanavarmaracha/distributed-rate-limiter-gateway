package com.sriman.ratelimiter.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.sriman.ratelimiter.domain.AlgorithmType;
import com.sriman.ratelimiter.domain.RateLimitRule;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalTokenBucketRateLimiterEngineTest {

    private LocalTokenBucketRateLimiterEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LocalTokenBucketRateLimiterEngine();
    }

    @Test
    void tokenBucketShouldAllowUpToCapacityThenThrottle() {
        RateLimitRule rule = buildRule(1L, 3, 0.01, 10, AlgorithmType.TOKEN_BUCKET);

        assertTrue(engine.tryConsume("client-a", rule).allowed());
        assertTrue(engine.tryConsume("client-a", rule).allowed());
        RateLimitDecision third = engine.tryConsume("client-a", rule);
        assertTrue(third.allowed());
        assertEquals(0, third.remaining());

        RateLimitDecision blocked = engine.tryConsume("client-a", rule);
        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() >= 1);
    }

    @Test
    void slidingWindowShouldEnforceExactWindowLimit() {
        RateLimitRule rule = buildRule(2L, 2, 1.0, 5, AlgorithmType.SLIDING_WINDOW);

        assertTrue(engine.tryConsume("client-b", rule).allowed());
        assertTrue(engine.tryConsume("client-b", rule).allowed());
        RateLimitDecision third = engine.tryConsume("client-b", rule);
        assertFalse(third.allowed());
        assertEquals(AlgorithmType.SLIDING_WINDOW, third.algorithm());
    }

    @Test
    void concurrentRequestsShouldNotExceedCapacity() throws InterruptedException {
        int capacity = 20;
        int totalThreads = 50;
        RateLimitRule rule = buildRule(3L, capacity, 0.0001, 60, AlgorithmType.TOKEN_BUCKET);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (engine.tryConsume("concurrent-client", rule).allowed()) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        for (Future<?> f : futures) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                fail(e);
            } catch (TimeoutException e) {
                fail(e);
            }
        }
        pool.shutdown();

        assertEquals(capacity, allowedCount.get(), "Allowed requests must exactly match capacity under contention");
    }

    private RateLimitRule buildRule(Long id, int capacity, double refill, int windowSec, AlgorithmType algo) {
        RateLimitRule rule = new RateLimitRule();
        rule.setId(id);
        rule.setName("test-rule-" + id);
        rule.setPathPattern("/api/v1/demo/**");
        rule.setHttpMethod("GET");
        rule.setCapacity(capacity);
        rule.setRefillTokensPerSecond(refill);
        rule.setWindowSeconds(windowSec);
        rule.setAlgorithm(algo);
        rule.setEnabled(true);
        return rule;
    }
}
