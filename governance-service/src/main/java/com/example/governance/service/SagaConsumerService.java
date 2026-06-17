package com.example.governance.service;

import com.example.governance.dto.GovernanceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaConsumerService {

    private final PolicyService policyService;

    @KafkaListener(topics = "saga-events", groupId = "governance-group")
    public void consumeSagaEvent(GovernanceEvent event) {
        log.info("Received Saga event: type={}, policyId={}, actor={}", 
                event.getEventType(), event.getPolicyId(), event.getActor());

        if ("audit-failed".equals(event.getEventType())) {
            log.warn("Audit logging failed for Policy ID [{}] during [{}]. Triggering Saga compensating action.",
                    event.getPolicyId(), event.getFailedEventType());
            try {
                policyService.compensateAuditFailure(event.getPolicyId(), event.getActor(), event.getFailedEventType());
                log.info("Compensating action executed successfully for Policy ID [{}] and failed event [{}].",
                        event.getPolicyId(), event.getFailedEventType());
            } catch (Exception e) {
                log.error("Failed to execute compensating action for Policy ID [{}]: {}", 
                        event.getPolicyId(), e.getMessage(), e);
            }
        }
    }
}
