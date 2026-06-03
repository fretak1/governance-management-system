package com.example.audit_service.service;

import com.example.audit_service.dto.GovernanceEvent;
import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditConsumerServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditConsumerService auditConsumerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testConsumeEvent_Success() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        GovernanceEvent event = GovernanceEvent.builder()
                .eventType("policy-approved")
                .policyId(15L)
                .actor("manager")
                .timestamp(now)
                .build();

        AuditLog savedLog = AuditLog.builder()
                .id(1L)
                .eventType("policy-approved")
                .policyId(15L)
                .actor("manager")
                .timestamp(now)
                .build();

        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

        // Act
        auditConsumerService.consume(event);

        // Assert
        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(auditLogCaptor.capture());

        AuditLog capturedLog = auditLogCaptor.getValue();
        assertEquals("policy-approved", capturedLog.getEventType());
        assertEquals(15L, capturedLog.getPolicyId());
        assertEquals("manager", capturedLog.getActor());
        assertEquals(now, capturedLog.getTimestamp());
    }
}
