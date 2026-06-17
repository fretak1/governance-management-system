package com.example.governance.service;

import com.example.governance.dto.GovernanceEvent;
import com.example.governance.exception.InvalidStateTransitionException;
import com.example.governance.exception.ResourceNotFoundException;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.model.OutboxEvent;
import com.example.governance.model.OutboxEventStatus;
import com.example.governance.repository.PolicyRepository;
import com.example.governance.repository.OutboxEventRepository;
import com.example.governance.grpc.AuditGrpcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private AuditGrpcClient auditGrpcClient;

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
    void createPolicy_Success() throws Exception {
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy created = policyService.createPolicy(mockPolicy);

        assertNotNull(created);
        assertEquals(PolicyStatus.DRAFT, created.getStatus());
        assertEquals("Security Policy", created.getTitle());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxEventCaptor.capture());
        
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();
        assertEquals("policy-created", capturedEvent.getEventType());
        assertEquals(OutboxEventStatus.PENDING, capturedEvent.getStatus());
        
        GovernanceEvent governanceEvent = objectMapper.readValue(capturedEvent.getPayload(), GovernanceEvent.class);
        assertEquals(1L, governanceEvent.getPolicyId());
        assertEquals("admin", governanceEvent.getActor());
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
    void submitPolicy_Success() throws Exception {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy submitted = policyService.submitPolicy(1L, "user1");

        assertNotNull(submitted);
        assertEquals(PolicyStatus.PENDING_APPROVAL, submitted.getStatus());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxEventCaptor.capture());
        
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();
        assertEquals("policy-submitted", capturedEvent.getEventType());
        assertEquals(OutboxEventStatus.PENDING, capturedEvent.getStatus());

        GovernanceEvent governanceEvent = objectMapper.readValue(capturedEvent.getPayload(), GovernanceEvent.class);
        assertEquals(1L, governanceEvent.getPolicyId());
        assertEquals("user1", governanceEvent.getActor());
    }

    @Test
    void submitPolicy_InvalidState() {
        mockPolicy.setStatus(PolicyStatus.APPROVED);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.submitPolicy(1L, "user1"));
        verify(policyRepository, never()).save(any(Policy.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void approvePolicy_Success() throws Exception {
        mockPolicy.setStatus(PolicyStatus.PENDING_APPROVAL);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy approved = policyService.approvePolicy(1L, "manager1");

        assertNotNull(approved);
        assertEquals(PolicyStatus.APPROVED, approved.getStatus());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxEventCaptor.capture());
        
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();
        assertEquals("policy-approved", capturedEvent.getEventType());
        assertEquals(OutboxEventStatus.PENDING, capturedEvent.getStatus());

        GovernanceEvent governanceEvent = objectMapper.readValue(capturedEvent.getPayload(), GovernanceEvent.class);
        assertEquals(1L, governanceEvent.getPolicyId());
        assertEquals("manager1", governanceEvent.getActor());

        verify(auditGrpcClient, times(1)).logAudit("policy-approved", 1L, "manager1");
    }

    @Test
    void approvePolicy_InvalidState() {
        mockPolicy.setStatus(PolicyStatus.DRAFT);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.approvePolicy(1L, "manager1"));
        verify(policyRepository, never()).save(any(Policy.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        verify(auditGrpcClient, never()).logAudit(any(), any(), any());
    }

    @Test
    void rejectPolicy_Success() throws Exception {
        mockPolicy.setStatus(PolicyStatus.PENDING_APPROVAL);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        Policy rejected = policyService.rejectPolicy(1L, "manager1");

        assertNotNull(rejected);
        assertEquals(PolicyStatus.REJECTED, rejected.getStatus());

        verify(policyRepository, times(1)).save(mockPolicy);
        
        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxEventCaptor.capture());
        
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();
        assertEquals("policy-rejected", capturedEvent.getEventType());
        assertEquals(OutboxEventStatus.PENDING, capturedEvent.getStatus());

        GovernanceEvent governanceEvent = objectMapper.readValue(capturedEvent.getPayload(), GovernanceEvent.class);
        assertEquals(1L, governanceEvent.getPolicyId());
        assertEquals("manager1", governanceEvent.getActor());
    }

    @Test
    void rejectPolicy_InvalidState() {
        mockPolicy.setStatus(PolicyStatus.DRAFT);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        assertThrows(InvalidStateTransitionException.class, () -> policyService.rejectPolicy(1L, "manager1"));
        verify(policyRepository, never()).save(any(Policy.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void compensateAuditFailure_ForPolicyCreated_DeletesDraftPolicy() {
        mockPolicy.setStatus(PolicyStatus.DRAFT);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));

        policyService.compensateAuditFailure(1L, "auditor", "policy-created");

        verify(policyRepository).delete(mockPolicy);

        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        assertEquals("policy-creation-deleted", outboxEventCaptor.getValue().getEventType());
    }

    @Test
    void compensateAuditFailure_ForPolicySubmitted_RevertsToDraft() {
        mockPolicy.setStatus(PolicyStatus.PENDING_APPROVAL);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        policyService.compensateAuditFailure(1L, "auditor", "policy-submitted");

        assertEquals(PolicyStatus.DRAFT, mockPolicy.getStatus());
        verify(policyRepository).save(mockPolicy);

        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        assertEquals("policy-submission-reverted", outboxEventCaptor.getValue().getEventType());
    }

    @Test
    void compensateAuditFailure_ForPolicyRejected_RevertsToPendingApproval() {
        mockPolicy.setStatus(PolicyStatus.REJECTED);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(mockPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(mockPolicy);

        policyService.compensateAuditFailure(1L, "auditor", "policy-rejected");

        assertEquals(PolicyStatus.PENDING_APPROVAL, mockPolicy.getStatus());
        verify(policyRepository).save(mockPolicy);

        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        assertEquals("policy-rejection-reverted", outboxEventCaptor.getValue().getEventType());
    }
}
