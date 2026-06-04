package com.example.governance;

import com.example.governance.dto.PolicyRequest;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.repository.PolicyRepository;
import com.example.governance.service.KafkaProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@AutoConfigureMockMvc
@Transactional
class PolicyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyRepository policyRepository;

    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaProducerService kafkaProducerService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testPolicyLifecycleIntegration() throws Exception {
        // 1. Create a Policy via REST API
        PolicyRequest request = new PolicyRequest();
        request.setTitle("Integration Test Policy");
        request.setDescription("End-to-End database and API validation");
        request.setCreatedBy("qa-user");

        MvcResult createResult = mockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Policy createdPolicy = objectMapper.readValue(responseBody, Policy.class);
        Long policyId = createdPolicy.getId();

        // Verify it exists in the active PostgreSQL database
        Optional<Policy> dbPolicyOpt = policyRepository.findById(policyId);
        assertTrue(dbPolicyOpt.isPresent());
        Policy dbPolicy = dbPolicyOpt.get();
        assertEquals("Integration Test Policy", dbPolicy.getTitle());
        assertEquals(PolicyStatus.DRAFT, dbPolicy.getStatus());
        verify(kafkaProducerService, atLeastOnce()).sendEvent(any());

        // 2. Submit the Policy via REST API
        mockMvc.perform(post("/policies/" + policyId + "/submit?actor=qa-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        // Verify status updated in the database
        dbPolicy = policyRepository.findById(policyId).orElseThrow();
        assertEquals(PolicyStatus.PENDING_APPROVAL, dbPolicy.getStatus());

        // 3. Approve the Policy via REST API
        mockMvc.perform(post("/policies/" + policyId + "/approve?actor=approver-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify status updated to APPROVED in the database
        dbPolicy = policyRepository.findById(policyId).orElseThrow();
        assertEquals(PolicyStatus.APPROVED, dbPolicy.getStatus());

        // 4. Retrieve the Policy by ID via REST API
        mockMvc.perform(get("/policies/" + policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(policyId))
                .andExpect(jsonPath("$.title").value("Integration Test Policy"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void testPolicyRejectLifecycleIntegration() throws Exception {
        // 1. Create a Policy via REST API
        PolicyRequest request = new PolicyRequest();
        request.setTitle("Reject Test Policy");
        request.setDescription("Rejection flow integration validation");
        request.setCreatedBy("qa-user");

        MvcResult createResult = mockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Policy createdPolicy = objectMapper.readValue(responseBody, Policy.class);
        Long policyId = createdPolicy.getId();

        // 2. Submit the Policy
        mockMvc.perform(post("/policies/" + policyId + "/submit?actor=qa-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        // 3. Reject the Policy
        mockMvc.perform(post("/policies/" + policyId + "/reject?actor=rejecter-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Verify status in DB
        Policy dbPolicy = policyRepository.findById(policyId).orElseThrow();
        assertEquals(PolicyStatus.REJECTED, dbPolicy.getStatus());

        // 4. Retrieve and verify
        mockMvc.perform(get("/policies/" + policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void testPolicyInvalidStateTransitionIntegration() throws Exception {
        // 1. Create a Policy via REST API
        PolicyRequest request = new PolicyRequest();
        request.setTitle("Invalid Transition Policy");
        request.setDescription("Will try to approve from DRAFT");
        request.setCreatedBy("qa-user");

        MvcResult createResult = mockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        Policy createdPolicy = objectMapper.readValue(responseBody, Policy.class);
        Long policyId = createdPolicy.getId();

        // 2. Try to Approve without submitting (DRAFT -> APPROVED is invalid)
        mockMvc.perform(post("/policies/" + policyId + "/approve?actor=approver-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot approve policy. Current status is DRAFT but must be PENDING_APPROVAL"));

        // Verify status remains DRAFT in the database
        Policy dbPolicy = policyRepository.findById(policyId).orElseThrow();
        assertEquals(PolicyStatus.DRAFT, dbPolicy.getStatus());
    }
}
