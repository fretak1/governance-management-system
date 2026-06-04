package com.example.audit_service.service;

import com.example.audit_service.dto.GovernanceEvent;
import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditConsumerServiceUnitTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditConsumerService auditConsumerService;

    private GovernanceEvent mockEvent;
    private AuditLog mockSavedLog;

    @BeforeEach
    void setUp() {
        mockEvent = GovernanceEvent.builder()
                .eventType("policy-created")
                .policyId(10L)
                .actor("admin")
                .timestamp(LocalDateTime.now())
                .build();

        mockSavedLog = AuditLog.builder()
                .id(100L)
                .eventType("policy-created")
                .policyId(10L)
                .actor("admin")
                .timestamp(mockEvent.getTimestamp())
                .build();
    }

    @Test
    void consume_Success() {
        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(mockSavedLog);

        auditConsumerService.consume(mockEvent);

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(auditLogCaptor.capture());

        AuditLog capturedLog = auditLogCaptor.getValue();
        assertNotNull(capturedLog);
        assertEquals("policy-created", capturedLog.getEventType());
        assertEquals(10L, capturedLog.getPolicyId());
        assertEquals("admin", capturedLog.getActor());
        assertEquals(mockEvent.getTimestamp(), capturedLog.getTimestamp());
    }

    @Test
    void consume_RepositoryThrowsException() {
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("Database error"));

        try {
            auditConsumerService.consume(mockEvent);
        } catch (RuntimeException e) {
            assertEquals("Database error", e.getMessage());
        }

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }
}
