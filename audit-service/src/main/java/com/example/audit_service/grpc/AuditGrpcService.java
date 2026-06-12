package com.example.audit_service.grpc;

import com.example.grpc.audit.AuditServiceGrpc;
import com.example.grpc.audit.LogAuditRequest;
import com.example.grpc.audit.LogAuditResponse;
import com.example.audit_service.model.AuditLog;
import com.example.audit_service.repository.AuditLogRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDateTime;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AuditGrpcService extends AuditServiceGrpc.AuditServiceImplBase {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void logAudit(LogAuditRequest request, StreamObserver<LogAuditResponse> responseObserver) {
        log.info("Received gRPC audit log request: eventType={}, policyId={}, actor={}",
                request.getEventType(), request.getPolicyId(), request.getActor());

        try {
            LocalDateTime timestamp = request.getTimestamp().isEmpty()
                    ? LocalDateTime.now()
                    : LocalDateTime.parse(request.getTimestamp());

            AuditLog auditLog = AuditLog.builder()
                    .eventType(request.getEventType() + " (gRPC)")
                    .policyId(request.getPolicyId())
                    .actor(request.getActor())
                    .timestamp(timestamp)
                    .build();

            AuditLog savedLog = auditLogRepository.save(auditLog);
            log.info("Successfully persisted gRPC audit log with ID: {}", savedLog.getId());

            LogAuditResponse response = LogAuditResponse.newBuilder()
                    .setId(savedLog.getId())
                    .setStatus("SUCCESS")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to persist gRPC audit log", e);
            responseObserver.onError(e);
        }
    }
}
