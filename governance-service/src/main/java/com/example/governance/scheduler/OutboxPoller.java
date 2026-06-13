package com.example.governance.scheduler;

import com.example.governance.model.OutboxEvent;
import com.example.governance.model.OutboxEventStatus;
import com.example.governance.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;

    @Scheduled(fixedDelay = 5000)
    public void pollOutbox() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEventStatus.PENDING);
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found {} pending outbox events to process", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                outboxEventProcessor.process(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event: id={}", event.getId(), e);
            }
        }
    }
}
