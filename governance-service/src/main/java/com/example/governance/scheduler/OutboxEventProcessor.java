package com.example.governance.scheduler;

import com.example.governance.dto.GovernanceEvent;
import com.example.governance.model.OutboxEvent;
import com.example.governance.model.OutboxEventStatus;
import com.example.governance.repository.OutboxEventRepository;
import com.example.governance.service.KafkaProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(OutboxEvent event) throws Exception {
        GovernanceEvent governanceEvent = objectMapper.readValue(event.getPayload(), GovernanceEvent.class);
        
        // Synchronously send event to Kafka
        kafkaProducerService.sendEventSync(governanceEvent);
        
        // Update status to PUBLISHED
        event.setStatus(OutboxEventStatus.PUBLISHED);
        outboxEventRepository.save(event);
        
        log.info("Successfully processed outbox event: id={}, type={}", event.getId(), event.getEventType());
    }
}
