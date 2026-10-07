package com.appcontrol.panel.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.*;
@Configuration
class OpenApiConfig {
    @Bean OpenAPI apiDocs() { return new OpenAPI().info(new Info().title("AppControl API").version("1.0.0").description("Firebase ID tokens; admin=true claim required for admin routes."))
        .components(new Components().addSecuritySchemes("firebaseBearer",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("Firebase ID token"))); }
}
