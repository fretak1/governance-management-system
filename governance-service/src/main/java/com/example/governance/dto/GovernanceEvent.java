package com.example.governance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernanceEvent {
    private String eventType;
    private Long policyId;
    private String actor;
    private LocalDateTime timestamp;
}