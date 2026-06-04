package com.example.governance.service;

import com.example.governance.dto.GovernanceEvent;
import com.example.governance.exception.InvalidStateTransitionException;
import com.example.governance.exception.ResourceNotFoundException;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private PolicyService policyService;

    private Policy mockPolicy;

    @BeforeEach
    void setUp() {
        mockPolicy = Policy.builder()
                .id(1L)
                .title("Security Policy")
                .description("Controls user security settings")
                .status(PolicyStatus.DRAFT)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createPolicy_Success() {
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy created = policyService.createPolicy(mockPolicy);

        assertNotNull(created);
        assertEquals(PolicyStatus.DRAFT, created.getStatus());
        assertEquals("Security Policy", created.getTitle());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<GovernanceEvent> eventCaptor = ArgumentCaptor.forClass(GovernanceEvent.class);
        verify(kafkaProducerService, times(1)).sendEvent(eventCaptor.capture());
        
        GovernanceEvent capturedEvent = eventCaptor.getValue();
        assertEquals("policy-created", capturedEvent.getEventType());
        assertEquals(1L, capturedEvent.getPolicyId());
        assertEquals("admin", capturedEvent.getActor());
    }

    @Test
    void getAllPolicies_Success() {
        when(policyRepository.findAll()).thenReturn(Collections.singletonList(mockPolicy));

        List<Policy> policies = policyService.getAllPolicies();

        assertNotNull(policies);
        assertEquals(1, policies.size());
        assertEquals("Security Policy", policies.get(0).getTitle());
        verify(policyRepository, times(1)).findAll();
    }

    @Test
    void getPolicyById_Found() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        Policy found = policyService.getPolicyById(1L);

        assertNotNull(found);
        assertEquals(1L, found.getId());
        verify(policyRepository, times(1)).findById(1L);
    }

    @Test
    void getPolicyById_NotFound() {
        when(policyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> policyService.getPolicyById(1L));
        verify(policyRepository, times(1)).findById(1L);
    }

    @Test
    void submitPolicy_Success() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy submitted = policyService.submitPolicy(1L, "user1");

        assertNotNull(submitted);
        assertEquals(PolicyStatus.PENDING_APPROVAL, submitted.getStatus());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<GovernanceEvent> eventCaptor = ArgumentCaptor.forClass(GovernanceEvent.class);
        verify(kafkaProducerService, times(1)).sendEvent(eventCaptor.capture());
        
        GovernanceEvent capturedEvent = eventCaptor.getValue();
        assertEquals("policy-submitted", capturedEvent.getEventType());
        assertEquals(1L, capturedEvent.getPolicyId());
        assertEquals("user1", capturedEvent.getActor());
    }

    @Test
    void submitPolicy_InvalidState() {
        mockPolicy.setStatus(PolicyStatus.APPROVED);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.submitPolicy(1L, "user1"));
        verify(policyRepository, never()).save(any(Policy.class));
        verify(kafkaProducerService, never()).sendEvent(any(GovernanceEvent.class));
    }

    @Test
    void approvePolicy_Success() {
        mockPolicy.setStatus(PolicyStatus.PENDING_APPROVAL);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy approved = policyService.approvePolicy(1L, "manager1");

        assertNotNull(approved);
        assertEquals(PolicyStatus.APPROVED, approved.getStatus());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<GovernanceEvent> eventCaptor = ArgumentCaptor.forClass(GovernanceEvent.class);
        verify(kafkaProducerService, times(1)).sendEvent(eventCaptor.capture());
        
        GovernanceEvent capturedEvent = eventCaptor.getValue();
        assertEquals("policy-approved", capturedEvent.getEventType());
        assertEquals(1L, capturedEvent.getPolicyId());
        assertEquals("manager1", capturedEvent.getActor());
    }

    @Test
    void approvePolicy_InvalidState() {
        mockPolicy.setStatus(PolicyStatus.DRAFT);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.approvePolicy(1L, "manager1"));
        verify(policyRepository, never()).save(any(Policy.class));
        verify(kafkaProducerService, never()).sendEvent(any(GovernanceEvent.class));
    }

    @Test
    void rejectPolicy_Success() {
        mockPolicy.setStatus(PolicyStatus.PENDING_APPROVAL);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy rejected = policyService.rejectPolicy(1L, "manager1");

        assertNotNull(rejected);
        assertEquals(PolicyStatus.REJECTED, rejected.getStatus());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<GovernanceEvent> eventCaptor = ArgumentCaptor.forClass(GovernanceEvent.class);
        verify(kafkaProducerService, times(1)).sendEvent(eventCaptor.capture());
        
        GovernanceEvent capturedEvent = eventCaptor.getValue();
        assertEquals("policy-rejected", capturedEvent.getEventType());
        assertEquals(1L, capturedEvent.getPolicyId());
        assertEquals("manager1", capturedEvent.getActor());
    }

    @Test
    void rejectPolicy_InvalidState() {
        mockPolicy.setStatus(PolicyStatus.DRAFT);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.rejectPolicy(1L, "manager1"));
        verify(policyRepository, never()).save(any(Policy.class));
        verify(kafkaProducerService, never()).sendEvent(any(GovernanceEvent.class));
    }
}
