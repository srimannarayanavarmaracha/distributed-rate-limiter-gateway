package com.sriman.ratelimiter.web;

import com.sriman.ratelimiter.service.RateLimitRuleService;
import com.sriman.ratelimiter.web.dto.RuleRequest;
import com.sriman.ratelimiter.web.dto.RuleResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rules")
public class RateLimitRuleController {

    private final RateLimitRuleService ruleService;

    public RateLimitRuleController(RateLimitRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<RuleResponse> listRules() {
        return ruleService.findAll().stream().map(RuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse createRule(@Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(ruleService.create(request));
    }

    @PutMapping("/{id}")
    public RuleResponse updateRule(@PathVariable Long id, @Valid @RequestBody RuleRequest request) {
        return RuleResponse.from(ruleService.update(id, request));
    }

    @PatchMapping("/{id}/toggle")
    public RuleResponse toggleRule(@PathVariable Long id) {
        return RuleResponse.from(ruleService.toggle(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable Long id) {
        ruleService.delete(id);
    }
}
