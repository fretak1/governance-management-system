package com.example.governance.controller;

import com.example.governance.dto.PolicyRequest;
import com.example.governance.model.Policy;
import com.example.governance.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
@Tag(name = "Policy Management", description = "Endpoints for policy lifecycle operations (creation, submission, approval, and rejection)")
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Policy createPolicy(@Valid @RequestBody PolicyRequest request) {
        Policy policy = new Policy();
        policy.setTitle(request.getTitle());
        policy.setDescription(request.getDescription());
        policy.setCreatedBy(request.getCreatedBy());
        return policyService.createPolicy(policy);
    }

    @GetMapping
    public List<Policy> getAllPolicies() {
        return policyService.getAllPolicies();
    }

    @GetMapping("/{id}")
    public Policy getPolicyById(@PathVariable Long id) {
        return policyService.getPolicyById(id);
    }

    @PostMapping("/{id}/submit")
    public Policy submitPolicy(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "system") String actor) {
        return policyService.submitPolicy(id, actor);
    }

    @PostMapping("/{id}/approve")
    public Policy approvePolicy(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "system") String actor) {
        return policyService.approvePolicy(id, actor);
    }

    @PostMapping("/{id}/reject")
    public Policy rejectPolicy(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "system") String actor) {
        return policyService.rejectPolicy(id, actor);
    }
}