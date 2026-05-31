package com.example.governance.service;

import com.example.governance.exception.InvalidStateTransitionException;
import com.example.governance.exception.ResourceNotFoundException;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.repository.PolicyRepository;
import com.example.governance.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private PolicyServiceImpl policyService;

    private Policy mockPolicy;

    @BeforeEach
    void setUp() {
        mockPolicy = Policy.builder()
                .id(1L)
                .title("Test Policy")
                .description("Test Description")
                .createdBy("admin")
                .status(PolicyStatus.DRAFT)
                .build();
    }

    @Test
    void createPolicy_ShouldSaveWithDraftStatus() {
        when(policyRepository.save(any(Policy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Policy created = policyService.createPolicy(mockPolicy);

        assertNotNull(created);
        assertEquals(PolicyStatus.DRAFT, created.getStatus());
        verify(policyRepository, times(1)).save(mockPolicy);
    }

    @Test
    void submitPolicy_FromDraft_ShouldTransitionToPendingApproval() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Policy submitted = policyService.submitPolicy(1L);

        assertEquals(PolicyStatus.PENDING_APPROVAL, submitted.getStatus());
        verify(policyRepository, times(1)).save(mockPolicy);
    }

    @Test
    void submitPolicy_FromNonDraft_ShouldThrowException() {
        mockPolicy.setStatus(PolicyStatus.APPROVED);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.submitPolicy(1L));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    void approvePolicy_FromPendingApproval_ShouldTransitionToApproved() {
        mockPolicy.setStatus(PolicyStatus.PENDING_APPROVAL);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Policy approved = policyService.approvePolicy(1L);

        assertEquals(PolicyStatus.APPROVED, approved.getStatus());
        verify(policyRepository, times(1)).save(mockPolicy);
    }

    @Test
    void approvePolicy_FromDraft_ShouldThrowException() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.approvePolicy(1L));
        verify(policyRepository, never()).save(any(Policy.class));
    }
}