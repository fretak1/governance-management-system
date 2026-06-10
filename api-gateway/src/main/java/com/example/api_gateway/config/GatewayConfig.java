package com.example.api_gateway.config;

import com.example.api_gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public GatewayConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Public authentication endpoints in user-service
                .route("user-service-public", r -> r.path("/auth/register", "/auth/login")
                        .uri("lb://USER-SERVICE"))
                // Protected governance-service endpoints
                .route("governance-service", r -> r.path("/policies", "/policies/**")
                        .filters(f -> f.filter(jwtFilter))
                        .uri("lb://GOVERNANCE-SERVICE"))
                // Protected audit-service endpoints
                .route("audit-service", r -> r.path("/audits", "/audits/**")
                        .filters(f -> f.filter(jwtFilter))
                        .uri("lb://AUDIT-SERVICE"))
                .build();
    }
}
