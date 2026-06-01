package com.example.governance.service.impl;

import com.example.governance.exception.InvalidStateTransitionException;
import com.example.governance.exception.ResourceNotFoundException;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.repository.PolicyRepository;
import com.example.governance.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;

    @Override
    @Transactional
    public Policy createPolicy(Policy policy) {
        policy.setStatus(PolicyStatus.DRAFT);
        return policyRepository.save(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Policy> getAllPolicies() {
        return policyRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Policy getPolicyById(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with id: " + id));
    }

    @Override
    @Transactional
    public Policy submitPolicy(Long id, String actor) {
        Policy policy = getPolicyById(id);
        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new InvalidStateTransitionException(
                "Cannot submit policy. Current status is " + policy.getStatus() + " but must be DRAFT"
            );
        }
        policy.setStatus(PolicyStatus.PENDING_APPROVAL);
        return policyRepository.save(policy);
    }

    @Override
    @Transactional
    public Policy approvePolicy(Long id, String actor) {
        Policy policy = getPolicyById(id);
        if (policy.getStatus() != PolicyStatus.PENDING_APPROVAL) {
            throw new InvalidStateTransitionException(
                "Cannot approve policy. Current status is " + policy.getStatus() + " but must be PENDING_APPROVAL"
            );
        }
        policy.setStatus(PolicyStatus.APPROVED);
        return policyRepository.save(policy);
    }

    @Override
    @Transactional
    public Policy rejectPolicy(Long id, String actor) {
        Policy policy = getPolicyById(id);
        if (policy.getStatus() != PolicyStatus.PENDING_APPROVAL) {
            throw new InvalidStateTransitionException(
                "Cannot reject policy. Current status is " + policy.getStatus() + " but must be PENDING_APPROVAL"
            );
        }
        policy.setStatus(PolicyStatus.REJECTED);
        return policyRepository.save(policy);
    }
}