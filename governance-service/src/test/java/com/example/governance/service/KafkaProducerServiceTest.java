package com.example.governance.service;

import com.example.governance.dto.GovernanceEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, GovernanceEvent> kafkaTemplate;

    @InjectMocks
    private KafkaProducerService kafkaProducerService;

    private GovernanceEvent mockEvent;

    @BeforeEach
    void setUp() {
        mockEvent = GovernanceEvent.builder()
                .eventType("policy-created")
                .policyId(1L)
                .actor("admin")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void sendEvent_Success() {
        @SuppressWarnings("unchecked")
        SendResult<String, GovernanceEvent> sendResult = mock(SendResult.class);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        when(recordMetadata.partition()).thenReturn(0);
        when(recordMetadata.offset()).thenReturn(0L);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);

        CompletableFuture<SendResult<String, GovernanceEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq("governance-events"), eq("1"), any(GovernanceEvent.class)))
                .thenReturn(future);

        kafkaProducerService.sendEvent(mockEvent);

        verify(kafkaTemplate, times(1)).send(eq("governance-events"), eq("1"), any(GovernanceEvent.class));
    }

    @Test
    void sendEvent_Failure() {
        CompletableFuture<SendResult<String, GovernanceEvent>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka connection timeout"));

        when(kafkaTemplate.send(eq("governance-events"), eq("1"), any(GovernanceEvent.class)))
                .thenReturn(future);

        kafkaProducerService.sendEvent(mockEvent);

        verify(kafkaTemplate, times(1)).send(eq("governance-events"), eq("1"), any(GovernanceEvent.class));
    }

    @Test
    void sendEventSync_Success() throws Exception {
        @SuppressWarnings("unchecked")
        SendResult<String, GovernanceEvent> sendResult = mock(SendResult.class);
        CompletableFuture<SendResult<String, GovernanceEvent>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq("governance-events"), eq("1"), any(GovernanceEvent.class)))
                .thenReturn(future);

        kafkaProducerService.sendEventSync(mockEvent);

        verify(kafkaTemplate, times(1)).send(eq("governance-events"), eq("1"), any(GovernanceEvent.class));
    }

    @Test
    void sendEventSync_Failure() {
        CompletableFuture<SendResult<String, GovernanceEvent>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(eq("governance-events"), eq("1"), any(GovernanceEvent.class)))
                .thenReturn(future);

        assertThrows(Exception.class, () -> kafkaProducerService.sendEventSync(mockEvent));
        verify(kafkaTemplate, times(1)).send(eq("governance-events"), eq("1"), any(GovernanceEvent.class));
    }
}

