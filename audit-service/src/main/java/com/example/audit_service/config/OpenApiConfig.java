package com.example.audit_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI auditOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Audit Service API")
                        .description("API for querying and searching system audit logs.")
                        .version("1.0"));
    }
}
