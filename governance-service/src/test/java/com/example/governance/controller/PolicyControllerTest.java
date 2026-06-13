package com.example.governance.controller;

import com.example.governance.dto.PolicyRequest;
import com.example.governance.exception.InvalidStateTransitionException;
import com.example.governance.exception.ResourceNotFoundException;
import com.example.governance.model.Policy;
import com.example.governance.model.PolicyStatus;
import com.example.governance.service.PolicyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyService policyService;

    private ObjectMapper objectMapper;

    private Policy mockPolicy;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockPolicy = Policy.builder()
                .id(1L)
                .title("Data Privacy Policy")
                .description("Protects sensitive user data")
                .status(PolicyStatus.DRAFT)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createPolicy_Success() throws Exception {
        PolicyRequest request = new PolicyRequest();
        request.setTitle("Data Privacy Policy");
        request.setDescription("Protects sensitive user data");
        request.setCreatedBy("admin");

        when(policyService.createPolicy(any(Policy.class))).thenReturn(mockPolicy);

        mockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Data Privacy Policy"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value("admin"));
    }

    @Test
    void createPolicy_ValidationError() throws Exception {
        PolicyRequest request = new PolicyRequest();
        request.setTitle(""); // Invalid blank title
        request.setCreatedBy("admin");

        mockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllPolicies_Success() throws Exception {
        when(policyService.getAllPolicies()).thenReturn(Collections.singletonList(mockPolicy));

        mockMvc.perform(get("/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Data Privacy Policy"));
    }

    @Test
    void getPolicyById_Success() throws Exception {
        when(policyService.getPolicyById(1L)).thenReturn(mockPolicy);

        mockMvc.perform(get("/policies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Data Privacy Policy"));
    }

    @Test
    void getPolicyById_NotFound() throws Exception {
        when(policyService.getPolicyById(1L)).thenThrow(new ResourceNotFoundException("Policy not found with id: 1"));

        mockMvc.perform(get("/policies/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Policy not found with id: 1"));
    }

    @Test
    void submitPolicy_Success() throws Exception {
        mockPolicy.setStatus(PolicyStatus.PENDING_APPROVAL);
        when(policyService.submitPolicy(eq(1L), eq("user1"))).thenReturn(mockPolicy);

        mockMvc.perform(post("/policies/1/submit").header("X-Username", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void submitPolicy_InvalidTransition() throws Exception {
        when(policyService.submitPolicy(eq(1L), eq("user1")))
                .thenThrow(new InvalidStateTransitionException("Cannot submit policy. Current status is APPROVED but must be DRAFT"));

        mockMvc.perform(post("/policies/1/submit").header("X-Username", "user1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot submit policy. Current status is APPROVED but must be DRAFT"));
    }

    @Test
    void approvePolicy_Success() throws Exception {
        mockPolicy.setStatus(PolicyStatus.APPROVED);
        when(policyService.approvePolicy(eq(1L), eq("manager1"))).thenReturn(mockPolicy);

        mockMvc.perform(post("/policies/1/approve").header("X-Username", "manager1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectPolicy_Success() throws Exception {
        mockPolicy.setStatus(PolicyStatus.REJECTED);
        when(policyService.rejectPolicy(eq(1L), eq("manager1"))).thenReturn(mockPolicy);

        mockMvc.perform(post("/policies/1/reject").header("X-Username", "manager1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void approvePolicy_InvalidTransition() throws Exception {
        when(policyService.approvePolicy(eq(1L), eq("manager1")))
                .thenThrow(new InvalidStateTransitionException("Cannot approve policy. Current status is DRAFT but must be PENDING_APPROVAL"));

        mockMvc.perform(post("/policies/1/approve").header("X-Username", "manager1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot approve policy. Current status is DRAFT but must be PENDING_APPROVAL"));
    }

    @Test
    void rejectPolicy_InvalidTransition() throws Exception {
        when(policyService.rejectPolicy(eq(1L), eq("manager1")))
                .thenThrow(new InvalidStateTransitionException("Cannot reject policy. Current status is DRAFT but must be PENDING_APPROVAL"));

        mockMvc.perform(post("/policies/1/reject").header("X-Username", "manager1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot reject policy. Current status is DRAFT but must be PENDING_APPROVAL"));
    }

    @Test
    void submitPolicy_NotFound() throws Exception {
        when(policyService.submitPolicy(eq(999L), eq("user1")))
                .thenThrow(new ResourceNotFoundException("Policy not found with id: 999"));

        mockMvc.perform(post("/policies/999/submit").header("X-Username", "user1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Policy not found with id: 999"));
    }

    @Test
    void approvePolicy_NotFound() throws Exception {
        when(policyService.approvePolicy(eq(999L), eq("manager1")))
                .thenThrow(new ResourceNotFoundException("Policy not found with id: 999"));

        mockMvc.perform(post("/policies/999/approve").header("X-Username", "manager1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Policy not found with id: 999"));
    }

    @Test
    void rejectPolicy_NotFound() throws Exception {
        when(policyService.rejectPolicy(eq(999L), eq("manager1")))
                .thenThrow(new ResourceNotFoundException("Policy not found with id: 999"));

        mockMvc.perform(post("/policies/999/reject").header("X-Username", "manager1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Policy not found with id: 999"));
    }

    @Test
    void createPolicy_ValidationError_MissingCreatedBy() throws Exception {
        PolicyRequest request = new PolicyRequest();
        request.setTitle("Data Privacy Policy");
        request.setCreatedBy(""); // Invalid blank createdBy

        mockMvc.perform(post("/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllPolicies_Empty() throws Exception {
        when(policyService.getAllPolicies()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
