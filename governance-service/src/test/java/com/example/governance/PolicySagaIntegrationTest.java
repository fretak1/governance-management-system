package com.example.governance;

import com.example.governance.dto.GovernanceEvent;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.repository.PolicyRepository;
import com.example.governance.repository.OutboxEventRepository;
import com.example.governance.scheduler.OutboxPoller;
import com.example.governance.service.PolicyService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.governance.grpc.AuditGrpcClient;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"governance-events", "saga-events"})
@DirtiesContext
class PolicySagaIntegrationTest {

    @MockitoBean
    private AuditGrpcClient auditGrpcClient;

    @Autowired
    private PolicyService policyService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPoller outboxPoller;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, GovernanceEvent> kafkaTemplate;

    private Consumer<String, GovernanceEvent> governanceEventsConsumer;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        policyRepository.deleteAll();

        // Setup consumer to listen to governance-events (simulating audit-service listening)
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-saga-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonDeserializer");
        consumerProps.put("spring.json.trusted.packages", "*");
        consumerProps.put("spring.json.use.type.headers", "false");
        consumerProps.put("spring.json.value.default.type", "com.example.governance.dto.GovernanceEvent");

        ConsumerFactory<String, GovernanceEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        governanceEventsConsumer = consumerFactory.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(governanceEventsConsumer, "governance-events");

        // Drain any existing records in the topic
        try {
            KafkaTestUtils.getRecords(governanceEventsConsumer, java.time.Duration.ofMillis(500));
        } catch (Exception e) {
            // Ignore
        }
    }

    @AfterEach
    void tearDown() {
        if (governanceEventsConsumer != null) {
            governanceEventsConsumer.close();
        }
        outboxEventRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    void testSagaCompensatingTransactionOnAuditFailure() throws Exception {
        // 1. Create a draft policy
        Policy policy = new Policy();
        policy.setTitle("Saga Test Policy");
        policy.setDescription("Testing Saga compensating action");
        policy.setCreatedBy("saga_tester");
        Policy savedPolicy = policyService.createPolicy(policy);
        Long policyId = savedPolicy.getId();

        // 2. Submit the policy
        policyService.submitPolicy(policyId, "saga_tester");

        // 3. Approve the policy
        policyService.approvePolicy(policyId, "saga_tester");

        // Verify it was successfully set to APPROVED initially
        Policy dbPolicy = policyRepository.findById(policyId).orElseThrow();
        assertEquals(PolicyStatus.APPROVED, dbPolicy.getStatus());

        // 4. Run outbox poller to publish events to "governance-events"
        outboxPoller.pollOutbox();

        // 5. Drain governance-events and find the policy-approved event
        // The outbox will publish policy-created, policy-submitted, AND policy-approved;
        // we must read all records and locate the right one.
        ConsumerRecord<String, GovernanceEvent> approvedRecord = null;
        long deadline = System.currentTimeMillis() + 10_000;
        while (approvedRecord == null && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, GovernanceEvent> records =
                    KafkaTestUtils.getRecords(governanceEventsConsumer, java.time.Duration.ofMillis(500));
            for (ConsumerRecord<String, GovernanceEvent> r : records) {
                if ("policy-approved".equals(r.value().getEventType())) {
                    approvedRecord = r;
                    break;
                }
            }
        }
        assertNotNull(approvedRecord, "Expected to find a policy-approved event in governance-events");
        assertEquals("saga_tester", approvedRecord.value().getActor());

        // 6. Simulate audit-service failure by publishing "audit-failed" to "saga-events"
        GovernanceEvent auditFailedEvent = GovernanceEvent.builder()
                .eventType("audit-failed")
                .failedEventType("policy-approved")
                .policyId(policyId)
                .actor("saga_tester")
                .build();
        
        kafkaTemplate.send("saga-events", String.valueOf(policyId), auditFailedEvent).get();

        // 7. Await the compensating transaction (SagaConsumerService should revert approval status to PENDING_APPROVAL)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Policy updatedPolicy = policyRepository.findById(policyId).orElseThrow();
            assertEquals(PolicyStatus.PENDING_APPROVAL, updatedPolicy.getStatus());
        });

        // 8. Verify that a "policy-approval-reverted" event has been added to the outbox
        outboxPoller.pollOutbox();
        boolean hasRevertedEvent = outboxEventRepository.findAll().stream()
                .anyMatch(e -> "policy-approval-reverted".equals(e.getEventType()));
        assertEquals(true, hasRevertedEvent);
    }
}
