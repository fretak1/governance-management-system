package com.example.governance.service;

import com.example.governance.dto.GovernanceEvent;
import com.example.governance.exception.InvalidStateTransitionException;
import com.example.governance.exception.ResourceNotFoundException;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final KafkaProducerService kafkaProducerService;

    private void publishEvent(String eventType, Long policyId, String actor) {
        GovernanceEvent event = GovernanceEvent.builder()
                .eventType(eventType)
                .policyId(policyId)
                .actor(actor)
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendEvent(event);
    }

    @Transactional
    public Policy createPolicy(Policy policy) {
        policy.setStatus(PolicyStatus.DRAFT);
        Policy savedPolicy = policyRepository.save(policy);
        publishEvent("policy-created", savedPolicy.getId(), savedPolicy.getCreatedBy());
        return savedPolicy;
    }

    @Transactional(readOnly = true)
    public List<Policy> getAllPolicies() {
        return policyRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Policy getPolicyById(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found with id: " + id));
    }

    @Transactional
    public Policy submitPolicy(Long id, String actor) {
        Policy policy = getPolicyById(id);
        if (policy.getStatus() != PolicyStatus.DRAFT) {
            throw new InvalidStateTransitionException(
                "Cannot submit policy. Current status is " + policy.getStatus() + " but must be DRAFT"
            );
        }
        policy.setStatus(PolicyStatus.PENDING_APPROVAL);
        Policy savedPolicy = policyRepository.save(policy);
        publishEvent("policy-submitted", savedPolicy.getId(), actor);
        return savedPolicy;
    }

    @Transactional
    public Policy approvePolicy(Long id, String actor) {
        Policy policy = getPolicyById(id);
        if (policy.getStatus() != PolicyStatus.PENDING_APPROVAL) {
            throw new InvalidStateTransitionException(
                "Cannot approve policy. Current status is " + policy.getStatus() + " but must be PENDING_APPROVAL"
            );
        }
        policy.setStatus(PolicyStatus.APPROVED);
        Policy savedPolicy = policyRepository.save(policy);
        publishEvent("policy-approved", savedPolicy.getId(), actor);
        return savedPolicy;
    }

    @Transactional
    public Policy rejectPolicy(Long id, String actor) {
        Policy policy = getPolicyById(id);
        if (policy.getStatus() != PolicyStatus.PENDING_APPROVAL) {
            throw new InvalidStateTransitionException(
                "Cannot reject policy. Current status is " + policy.getStatus() + " but must be PENDING_APPROVAL"
            );
        }
        policy.setStatus(PolicyStatus.REJECTED);
        Policy savedPolicy = policyRepository.save(policy);
        publishEvent("policy-rejected", savedPolicy.getId(), actor);
        return savedPolicy;
    }
}