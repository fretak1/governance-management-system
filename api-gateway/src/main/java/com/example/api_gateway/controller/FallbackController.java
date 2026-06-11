package com.example.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/governance")
    public Mono<ResponseEntity<Map<String, Object>>> governanceFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Governance service is currently unavailable. Please try again later.");
        response.put("status", 503);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }

    @RequestMapping("/audit")
    public Mono<ResponseEntity<Map<String, Object>>> auditFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Audit service is currently unavailable. Please try again later.");
        response.put("status", 503);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
