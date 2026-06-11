package com.example.api_gateway.config;

import com.example.api_gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public GatewayConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // To allow 10 requests per 60 seconds:
        // replenishRate: 1 token per second
        // burstCapacity: 60 tokens maximum (allows burst of 10 requests)
        // requestedTokens: 6 tokens consumed per request (10 requests * 6 = 60 tokens)
        return new RedisRateLimiter(1, 60, 6);
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String username = exchange.getRequest().getHeaders().getFirst("X-Username");
            if (username != null) {
                return Mono.just(username);
            }
            String ipAddress = exchange.getRequest().getRemoteAddress() != null 
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                    : "anonymous";
            return Mono.just(ipAddress);
        };
    }

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service-public", r -> r.path("/auth/register", "/auth/login")
                        .uri("lb://USER-SERVICE"))
                .route("governance-service", r -> r.path("/policies", "/policies/**")
                        .filters(f -> f.filter(jwtFilter)
                                .circuitBreaker(c -> c.setName("governanceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/governance"))
                                .requestRateLimiter(rl -> rl.setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(userKeyResolver())))
                        .uri("lb://GOVERNANCE-SERVICE"))
                .route("audit-service", r -> r.path("/audits", "/audits/**")
                        .filters(f -> f.filter(jwtFilter)
                                .circuitBreaker(c -> c.setName("auditCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/audit"))
                                .requestRateLimiter(rl -> rl.setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(userKeyResolver())))
                        .uri("lb://AUDIT-SERVICE"))
                .build();
    }
}
