package com.example.audit_service.controller;

import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audits")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAll();
    }

    @GetMapping("/policy/{policyId}")
    public List<AuditLog> getAuditLogsByPolicyId(@PathVariable Long policyId) {
        return auditLogRepository.findByPolicyId(policyId);
    }
}
