package com.example.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("governance-service", r -> r.path("/policies", "/policies/**")
                        .uri("lb://GOVERNANCE-SERVICE"))
                .route("audit-service", r -> r.path("/audits", "/audits/**")
                        .uri("lb://AUDIT-SERVICE"))
                .build();
    }
}
