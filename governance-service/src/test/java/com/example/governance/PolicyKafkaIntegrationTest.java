package com.example.governance;

import com.example.governance.dto.GovernanceEvent;
import com.example.governance.model.Policy;
import com.example.governance.repository.PolicyRepository;
import com.example.governance.repository.OutboxEventRepository;
import com.example.governance.scheduler.OutboxPoller;
import com.example.governance.service.PolicyService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"governance-events"})
class PolicyKafkaIntegrationTest {

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

    private Consumer<String, GovernanceEvent> consumer;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        policyRepository.deleteAll();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonDeserializer");
        consumerProps.put("spring.json.trusted.packages", "*");
        consumerProps.put("spring.json.use.type.headers", "false");
        consumerProps.put("spring.json.value.default.type", "com.example.governance.dto.GovernanceEvent");

        ConsumerFactory<String, GovernanceEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = consumerFactory.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "governance-events");
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        outboxEventRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    void testPolicyCreationPublishesKafkaEvent() {
        Policy policy = new Policy();
        policy.setTitle("Kafka Integration Policy");
        policy.setDescription("Testing Kafka event production");
        policy.setCreatedBy("kafka-tester");

        policyService.createPolicy(policy);

        // Manually trigger poller since policy and outbox are now committed
        outboxPoller.pollOutbox();

        ConsumerRecord<String, GovernanceEvent> record = KafkaTestUtils.getSingleRecord(consumer, "governance-events", java.time.Duration.ofSeconds(10));
        assertNotNull(record);
        
        GovernanceEvent event = record.value();
        assertEquals("policy-created", event.getEventType());
        assertEquals("kafka-tester", event.getActor());
    }
}
