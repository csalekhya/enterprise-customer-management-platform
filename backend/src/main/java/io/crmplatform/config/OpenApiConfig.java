package io.crmplatform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI metadata. Declaring the bearer scheme globally means Swagger UI's "Authorize" button
 * works for every endpoint without per-controller annotations.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI crmOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise Customer Management Platform API")
                        .version("1.0.0")
                        .description("""
                                B2B CRM backend: accounts, contacts, interactions, opportunities and \
                                pipeline analytics. All endpoints except /api/v1/auth/** require a \
                                bearer access token obtained from POST /api/v1/auth/login.""")
                        .contact(new Contact()
                                .name("enterprise-customer-management-platform contributors")
                                .url("https://github.com/csalekhya/enterprise-customer-management-platform"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://api.slatecrm.io").description("Production")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token, valid for 15 minutes")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
