package com.example.governance.grpc;

import com.example.grpc.audit.AuditServiceGrpc;
import com.example.grpc.audit.LogAuditRequest;
import com.example.grpc.audit.LogAuditResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AuditGrpcClient {

    @GrpcClient("audit-service")
    private AuditServiceGrpc.AuditServiceBlockingStub auditServiceBlockingStub;

    public void logAudit(String eventType, Long policyId, String actor) {
        try {
            LogAuditRequest request = LogAuditRequest.newBuilder()
                    .setEventType(eventType)
                    .setPolicyId(policyId)
                    .setActor(actor)
                    .setTimestamp(LocalDateTime.now().toString())
                    .build();

            log.info("Sending gRPC audit request for policy ID: {}", policyId);
            LogAuditResponse response = auditServiceBlockingStub.logAudit(request);
            log.info("Received gRPC audit response: id={}, status={}", response.getId(), response.getStatus());
        } catch (Exception e) {
            log.error("Failed to log audit via gRPC: {}", e.getMessage(), e);
        }
    }
}
