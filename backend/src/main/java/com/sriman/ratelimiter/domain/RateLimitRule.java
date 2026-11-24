package com.sriman.ratelimiter.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rate_limit_rules", indexes = {
    @Index(name = "idx_rule_enabled_pattern", columnList = "enabled, pathPattern")
})
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String pathPattern;

    @Column(nullable = false, length = 16)
    private String httpMethod;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private double refillTokensPerSecond;

    @Column(nullable = false)
    private int windowSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlgorithmType algorithm;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public double getRefillTokensPerSecond() { return refillTokensPerSecond; }
    public void setRefillTokensPerSecond(double refillTokensPerSecond) { this.refillTokensPerSecond = refillTokensPerSecond; }

    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }

    public AlgorithmType getAlgorithm() { return algorithm; }
    public void setAlgorithm(AlgorithmType algorithm) { this.algorithm = algorithm; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
