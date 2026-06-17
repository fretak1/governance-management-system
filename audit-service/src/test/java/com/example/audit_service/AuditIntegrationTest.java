package com.example.audit_service;

import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "grpc.server.port=0",
    "spring.autoconfigure.exclude="
    + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@AutoConfigureMockMvc
@Transactional
class AuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void testAuditQueryIntegration() throws Exception {
        // 1. Manually insert audit logs into the active PostgreSQL database
        AuditLog log1 = AuditLog.builder()
                .eventType("policy-created")
                .policyId(999L)
                .actor("integration-test-actor")
                .timestamp(LocalDateTime.now())
                .build();

        AuditLog log2 = AuditLog.builder()
                .eventType("policy-submitted")
                .policyId(999L)
                .actor("integration-test-actor")
                .timestamp(LocalDateTime.now().plusMinutes(1))
                .build();

        auditLogRepository.save(log1);
        auditLogRepository.save(log2);

        // Verify the database has persisted the records
        List<AuditLog> dbLogs = auditLogRepository.findByPolicyId(999L);
        assertEquals(2, dbLogs.size());

        // 2. Query all audit logs via REST API
        mockMvc.perform(get("/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.policyId == 999 && @.eventType == 'policy-created')]").exists())
                .andExpect(jsonPath("$[?(@.policyId == 999 && @.eventType == 'policy-submitted')]").exists());

        // 3. Query audit logs by policy ID via REST API
        mockMvc.perform(get("/audits/policy/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].policyId").value(999))
                .andExpect(jsonPath("$[0].actor").value("integration-test-actor"))
                .andExpect(jsonPath("$[1].policyId").value(999))
                .andExpect(jsonPath("$[1].eventType").value("policy-submitted"));
    }
}
