package com.sriman.ratelimiter.service;

import com.sriman.ratelimiter.domain.RateLimitRule;
import com.sriman.ratelimiter.repository.RateLimitRuleRepository;
import com.sriman.ratelimiter.web.dto.RuleRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

@Service
public class RateLimitRuleService {

    private final RateLimitRuleRepository repository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitRuleService(RateLimitRuleRepository repository) {
        this.repository = repository;
    }

    public List<RateLimitRule> findAll() {
        return repository.findAll();
    }

    public Optional<RateLimitRule> findMatchingRule(String path, String method) {
        List<RateLimitRule> enabledRules = repository.findByEnabledTrueOrderByIdAsc();
        return enabledRules.stream()
            .filter(rule -> matchesMethod(rule.getHttpMethod(), method))
            .filter(rule -> pathMatcher.match(rule.getPathPattern(), path))
            .findFirst();
    }

    @Transactional
    public RateLimitRule create(RuleRequest request) {
        repository.findByName(request.name()).ifPresent(existing -> {
            throw new IllegalArgumentException("Rule already exists with name: " + request.name());
        });
        RateLimitRule rule = new RateLimitRule();
        applyRequest(rule, request);
        return repository.save(rule);
    }

    @Transactional
    public RateLimitRule update(Long id, RuleRequest request) {
        RateLimitRule rule = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
        applyRequest(rule, request);
        return repository.save(rule);
    }

    @Transactional
    public RateLimitRule toggle(Long id) {
        RateLimitRule rule = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
        rule.setEnabled(!rule.isEnabled());
        return repository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }
        repository.deleteById(id);
    }

    private void applyRequest(RateLimitRule rule, RuleRequest request) {
        rule.setName(request.name().trim());
        rule.setPathPattern(request.pathPattern().trim());
        rule.setHttpMethod(request.httpMethod().trim().toUpperCase());
        rule.setCapacity(request.capacity());
        rule.setRefillTokensPerSecond(request.refillTokensPerSecond());
        rule.setWindowSeconds(request.windowSeconds());
        rule.setAlgorithm(request.algorithm());
        rule.setEnabled(request.enabled());
    }

    private boolean matchesMethod(String ruleMethod, String requestMethod) {
        return "*".equals(ruleMethod) || ruleMethod.equalsIgnoreCase(requestMethod);
    }
}
