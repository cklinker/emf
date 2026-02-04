package com.emf.controlplane.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for springdoc-openapi to generate OpenAPI 3.1 specification.
 * 
 * <p>This configuration provides:
 * <ul>
 *   <li>API metadata (title, version, description, contact, license)</li>
 *   <li>JWT Bearer authentication security scheme</li>
 *   <li>Server configuration for different environments</li>
 * </ul>
 * 
 * <p>The OpenAPI specification is available at:
 * <ul>
 *   <li>JSON format: /openapi (or /v3/api-docs)</li>
 *   <li>YAML format: /openapi.yaml (or /v3/api-docs.yaml)</li>
 *   <li>Swagger UI: /swagger-ui</li>
 * </ul>
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>9.1: Return valid OpenAPI 3.1 document in JSON format</li>
 *   <li>9.2: Return valid OpenAPI 3.1 document in YAML format</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenApiConfig.class);

    private static final String BEARER_JWT = "bearer-jwt";

    @Value("${spring.application.name:emf-control-plane}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * Creates the OpenAPI specification bean with API metadata and security configuration.
     * 
     * <p>The specification includes:
     * <ul>
     *   <li>API title, version, and description</li>
     *   <li>Contact information</li>
     *   <li>License information</li>
     *   <li>JWT Bearer authentication security scheme</li>
     *   <li>Server URLs for local development</li>
     * </ul>
     * 
     * @return Configured OpenAPI specification
     * 
     * Validates: Requirements 9.1, 9.2
     */
    @Bean
    public OpenAPI controlPlaneOpenAPI() {
        log.info("Configuring OpenAPI specification for {}", applicationName);

        return new OpenAPI()
                .info(apiInfo())
                .components(securityComponents())
                .servers(serverList());
    }

    /**
     * Creates the API info section with metadata about the Control Plane Service.
     */
    private Info apiInfo() {
        return new Info()
                .title("EMF Control Plane API")
                .version("1.0.0")
                .description("""
                        The EMF Control Plane Service provides REST APIs for managing all runtime 
                        configuration in the EMF (Enterprise Microservice Framework) platform.
                        
                        ## Features
                        
                        - **Collection Management**: Define and manage data collections with typed fields
                        - **Field Management**: Add, update, and remove fields from collections
                        - **Authorization Management**: Configure roles, policies, and access control
                        - **OIDC Provider Management**: Configure identity providers for authentication
                        - **UI Configuration**: Manage pages and menus for the admin interface
                        - **Package Management**: Export and import configuration packages
                        - **Migration Management**: Plan and track schema migrations
                        - **Resource Discovery**: Discover available collections and their schemas
                        
                        ## Authentication
                        
                        All endpoints (except health checks and OpenAPI documentation) require JWT 
                        authentication. Include a valid JWT token in the Authorization header:
                        
                        ```
                        Authorization: Bearer <your-jwt-token>
                        ```
                        
                        ## Authorization
                        
                        Write operations (POST, PUT, DELETE) require the ADMIN role. Read operations 
                        are available to all authenticated users.
                        """)
                .contact(new Contact()
                        .name("EMF Platform Team")
                        .email("platform@emf.io")
                        .url("https://github.com/emf-platform"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    /**
     * Creates the security components with JWT Bearer authentication scheme.
     * 
     * <p>The security scheme is configured as:
     * <ul>
     *   <li>Type: HTTP</li>
     *   <li>Scheme: Bearer</li>
     *   <li>Bearer Format: JWT</li>
     * </ul>
     * 
     * <p>Controllers can reference this scheme using:
     * {@code @SecurityRequirement(name = "bearer-jwt")}
     */
    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("""
                                JWT Bearer token authentication.
                                
                                Obtain a token from your configured OIDC provider and include it 
                                in the Authorization header as: `Bearer <token>`
                                
                                The token must contain valid claims including:
                                - `sub`: Subject (user identifier)
                                - `roles` or `realm_access.roles`: User roles for authorization
                                - `exp`: Expiration time
                                """));
    }

    /**
     * Creates the server list for the OpenAPI specification.
     * 
     * <p>Includes servers for:
     * <ul>
     *   <li>Local development</li>
     *   <li>Relative path (for deployment flexibility)</li>
     * </ul>
     */
    private List<Server> serverList() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local Development Server"),
                new Server()
                        .url("/")
                        .description("Current Server (relative)")
        );
    }
}
