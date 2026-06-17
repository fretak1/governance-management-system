package com.example.audit_service;

import com.example.audit_service.dto.GovernanceEvent;
import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
    "grpc.server.port=0",
    "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
    "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
})
@EmbeddedKafka(partitions = 1, topics = {"governance-events"})
@DirtiesContext
@Transactional
class AuditKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void testKafkaEventConsumption() throws Exception {
        // 1. Prepare a GovernanceEvent
        GovernanceEvent event = GovernanceEvent.builder()
                .eventType("policy-created")
                .policyId(12345L)
                .actor("kafka-integration-test")
                .timestamp(LocalDateTime.now())
                .build();

        // 2. Publish to the embedded topic
        kafkaTemplate.send("governance-events", String.valueOf(event.getPolicyId()), event).get(10, TimeUnit.SECONDS);

        // 3. Wait/Assert that it is consumed and saved in the repository
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditLog> logs = auditLogRepository.findByPolicyId(12345L);
            assertFalse(logs.isEmpty());
            assertEquals(1, logs.size());
            
            AuditLog log = logs.get(0);
            assertEquals("policy-created", log.getEventType());
            assertEquals("kafka-integration-test", log.getActor());
        });
    }
}
