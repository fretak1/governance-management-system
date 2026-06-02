package com.example.governance.service;

import com.example.governance.dto.GovernanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, GovernanceEvent> kafkaTemplate;
    private static final String TOPIC = "governance-events";

    public void sendEvent(GovernanceEvent event) {
        log.info("Sending Kafka event of type {} for Policy ID {}", event.getEventType(), event.getPolicyId());
        
        CompletableFuture<SendResult<String, GovernanceEvent>> future = kafkaTemplate.send(TOPIC, String.valueOf(event.getPolicyId()), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent event for Policy ID [{}] to partition [{}] at offset [{}]",
                        event.getPolicyId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send event for Policy ID [{}]: {}", event.getPolicyId(), ex.getMessage());
            }
        });
    }
}