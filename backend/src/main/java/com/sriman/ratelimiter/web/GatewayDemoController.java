package com.sriman.ratelimiter.web;

import com.sriman.ratelimiter.service.RateLimitEvaluationService;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GatewayDemoController {

    private final RateLimitEvaluationService evaluationService;

    public GatewayDemoController(RateLimitEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/demo/resource")
    public Map<String, Object> protectedResource(
        @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId
    ) {
        return Map.of(
            "status", "OK",
            "clientId", clientId,
            "message", "Request admitted through distributed rate limiter gateway",
            "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/metrics/summary")
    public Map<String, Object> metricsSummary() {
        long total = evaluationService.getTotalEvaluations();
        long blocked = evaluationService.getTotalBlocked();
        return Map.of(
            "totalEvaluations", total,
            "allowed", total - blocked,
            "blocked", blocked,
            "redisEnabled", evaluationService.isRedisEnabled()
        );
    }
}
