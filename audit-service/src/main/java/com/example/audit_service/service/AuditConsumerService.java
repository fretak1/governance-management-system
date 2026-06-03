package com.example.audit_service.service;

import com.example.audit_service.dto.GovernanceEvent;
import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditConsumerService {

    private final AuditLogRepository auditLogRepository;

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
                    event.getEventType(), event.getPolicyId(), e.getMessage(), e);
            throw e;
        }
    }
}
