package com.sriman.ratelimiter.repository;

import com.sriman.ratelimiter.domain.RateLimitRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, Long> {
    List<RateLimitRule> findByEnabledTrueOrderByIdAsc();
    Optional<RateLimitRule> findByName(String name);
}
