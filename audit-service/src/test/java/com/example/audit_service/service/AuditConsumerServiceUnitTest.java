package com.example.audit_service.service;

import com.example.audit_service.dto.GovernanceEvent;
import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditConsumerServiceUnitTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private KafkaTemplate<String, GovernanceEvent> kafkaTemplate;

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
        @SuppressWarnings("unchecked")
        SendResult<String, GovernanceEvent> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(eq("saga-events"), eq("10"), any(GovernanceEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        auditConsumerService.consume(mockEvent);

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));

        ArgumentCaptor<GovernanceEvent> eventCaptor = ArgumentCaptor.forClass(GovernanceEvent.class);
        verify(kafkaTemplate).send(eq("saga-events"), eq("10"), eventCaptor.capture());

        GovernanceEvent compensatingEvent = eventCaptor.getValue();
        assertEquals("audit-failed", compensatingEvent.getEventType());
        assertEquals("policy-created", compensatingEvent.getFailedEventType());
        assertEquals(10L, compensatingEvent.getPolicyId());
        assertEquals("admin", compensatingEvent.getActor());
    }
}
