package com.example.governance.service;

import com.example.governance.model.Policy;
import java.util.List;

public interface PolicyService {
    Policy createPolicy(Policy policy);
    List<Policy> getAllPolicies();
    Policy getPolicyById(Long id);
    Policy submitPolicy(Long id, String actor);
    Policy approvePolicy(Long id, String actor);
    Policy rejectPolicy(Long id, String actor);
}