package dev.naveencode.orderflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI orderflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("orderflow-api")
                        .version("1.0.0")
                        .description("""
                                Order management API.

                                Register or log in to receive a bearer token. Send it as `Authorization: Bearer <token>`.
                                USER can place and pay or cancel their own orders. ADMIN manages the catalog and can ship or deliver any order.

                                Status workflow: CREATED → PAID → SHIPPED → DELIVERED. CREATED or PAID can move to CANCELLED, which restores stock.

                                `POST /api/v1/orders` requires an `Idempotency-Key` header. The same key and body replay the original order. The same key with a different body is a conflict.

                                Errors use `application/problem+json` (RFC 7807). Amounts are decimals with scale 2.
                                """))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT from POST /api/v1/auth/register or /api/v1/auth/login")));
    }
}
