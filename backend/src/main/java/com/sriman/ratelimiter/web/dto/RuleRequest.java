package com.sriman.ratelimiter.web.dto;

import com.sriman.ratelimiter.domain.AlgorithmType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RuleRequest(
    @NotBlank String name,
    @NotBlank String pathPattern,
    @NotBlank String httpMethod,
    @Min(1) int capacity,
    @Positive double refillTokensPerSecond,
    @Min(1) int windowSeconds,
    @NotNull AlgorithmType algorithm,
    boolean enabled
) {}
