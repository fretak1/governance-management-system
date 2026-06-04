package com.example.audit_service.controller;

import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    private AuditLog mockAuditLog;

    @BeforeEach
    void setUp() {
        mockAuditLog = AuditLog.builder()
                .id(1L)
                .eventType("policy-created")
                .policyId(10L)
                .actor("admin")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllAuditLogs_Success() throws Exception {
        when(auditLogRepository.findAll()).thenReturn(Collections.singletonList(mockAuditLog));

        mockMvc.perform(get("/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].eventType").value("policy-created"))
                .andExpect(jsonPath("$[0].policyId").value(10))
                .andExpect(jsonPath("$[0].actor").value("admin"));
    }

    @Test
    void getAuditLogsByPolicyId_Success() throws Exception {
        when(auditLogRepository.findByPolicyId(eq(10L))).thenReturn(Collections.singletonList(mockAuditLog));

        mockMvc.perform(get("/audits/policy/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].eventType").value("policy-created"))
                .andExpect(jsonPath("$[0].policyId").value(10))
                .andExpect(jsonPath("$[0].actor").value("admin"));
    }

    @Test
    void getAllAuditLogs_Empty() throws Exception {
        when(auditLogRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAuditLogsByPolicyId_Empty() throws Exception {
        when(auditLogRepository.findByPolicyId(eq(999L))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/audits/policy/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
