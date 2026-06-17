package com.example.audit_service.service;

import com.example.audit_service.dto.GovernanceEvent;
import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditConsumerService {

    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, GovernanceEvent> kafkaTemplate;
    private static final String SAGA_TOPIC = "saga-events";

    @KafkaListener(topics = "governance-events", groupId = "audit-group")
    public void consume(GovernanceEvent event) {
        log.info("Received governance event: type={}, policyId={}, actor={}, timestamp={}",
                event.getEventType(), event.getPolicyId(), event.getActor(), event.getTimestamp());

        try {
            AuditLog auditLog = AuditLog.builder()
                    .eventType(event.getEventType())
                    .policyId(event.getPolicyId())
                    .actor(event.getActor())
                    .timestamp(event.getTimestamp())
                    .build();

            AuditLog savedLog = auditLogRepository.save(auditLog);
            log.info("Successfully persisted audit log with ID: {}", savedLog.getId());
        } catch (Exception e) {
            log.error("Failed to persist audit log for event of type {} and policy ID {}: {}",
                    event.getEventType(), event.getPolicyId(), e.getMessage());

            if (shouldTriggerCompensation(event.getEventType())) {
                publishCompensatingEvent(event.getPolicyId(), event.getActor(), event.getEventType());
            }
        }
    }

    private boolean shouldTriggerCompensation(String eventType) {
        return "policy-created".equals(eventType)
                || "policy-submitted".equals(eventType)
                || "policy-approved".equals(eventType)
                || "policy-rejected".equals(eventType);
    }

    private void publishCompensatingEvent(Long policyId, String actor, String failedEventType) {
        try {
            GovernanceEvent compensatingEvent = GovernanceEvent.builder()
                    .eventType("audit-failed")
                    .failedEventType(failedEventType)
                    .policyId(policyId)
                    .actor(actor)
                    .timestamp(LocalDateTime.now())
                    .build();

            log.info("Publishing compensating event (audit-failed) for Policy ID: {}", policyId);
            kafkaTemplate.send(SAGA_TOPIC, String.valueOf(policyId), compensatingEvent).get();
            log.info("Successfully published compensating event to {}", SAGA_TOPIC);
        } catch (Exception ex) {
            log.error("Failed to publish compensating event for Policy ID [{}]: {}", policyId, ex.getMessage(), ex);
        }
    }
}
