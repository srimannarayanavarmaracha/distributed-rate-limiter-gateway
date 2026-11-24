package com.sriman.ratelimiter.engine;

import com.sriman.ratelimiter.domain.AlgorithmType;

public record RateLimitDecision(
    boolean allowed,
    int limit,
    int remaining,
    long resetEpochSeconds,
    long retryAfterSeconds,
    String backend,
    AlgorithmType algorithm
) {}
