package com.example.governance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI governanceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Governance Service API")
                        .description("API for managing policy creation, submission, approval, and rejection.")
                        .version("1.0"));
    }
}
