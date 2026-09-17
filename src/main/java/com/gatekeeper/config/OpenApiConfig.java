package com.gatekeeper.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds the "bearerAuth" scheme so Swagger UI's Authorize button accepts a JWT access token. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI gatekeeperOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gatekeeper")
                        .description("Multi-Tenant RBAC & Dynamic Feature Flag Engine")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
