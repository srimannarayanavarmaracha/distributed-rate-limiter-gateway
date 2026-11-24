package com.sriman.ratelimiter.filter;

import com.sriman.ratelimiter.domain.RateLimitRule;
import com.sriman.ratelimiter.engine.RateLimitDecision;
import com.sriman.ratelimiter.service.RateLimitEvaluationService;
import com.sriman.ratelimiter.service.RateLimitRuleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces configured rate limit rules on protected gateway routes and injects
 * standard X-RateLimit-* and Retry-After response headers.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitRuleService ruleService;
    private final RateLimitEvaluationService evaluationService;

    public RateLimitFilter(RateLimitRuleService ruleService, RateLimitEvaluationService evaluationService) {
        this.ruleService = ruleService;
        this.evaluationService = evaluationService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/demo/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<RateLimitRule> ruleOpt = ruleService.findMatchingRule(
            request.getRequestURI(),
            request.getMethod()
        );

        if (ruleOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = ruleOpt.get();
        String clientKey = resolveClientKey(request);
        RateLimitDecision decision = evaluationService.evaluate(clientKey, rule);

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));
        response.setHeader("X-RateLimit-Algorithm", decision.algorithm().name());
        response.setHeader("X-RateLimit-Backend", decision.backend());

        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                {"type":"https://httpstatuses.com/429","title":"Too Many Requests","status":429,"detail":"Rate limit exceeded for rule '%s'. Retry after %d seconds."}"""
                .formatted(rule.getName(), decision.retryAfterSeconds()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-Client-Id");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
