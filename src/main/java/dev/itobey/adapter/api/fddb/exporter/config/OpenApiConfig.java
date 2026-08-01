package dev.itobey.adapter.api.fddb.exporter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi v2Api() {
        return GroupedOpenApi.builder()
                .group("v2")
                .pathsToMatch("/api/v2/**")
                .displayName("API v2")
                .build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FDDB Exporter API")
                        .description("""
                                REST API for the FDDB Exporter application.
                                
                                This API provides endpoints for:
                                - Querying FDDB nutrition data entries
                                - Exporting data from FDDB for specified date ranges
                                - Retrieving statistics and rolling averages
                                - Managing data migrations between storage backends
                                - Calculating correlations between data series
                                """)
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("FDDB Exporter")
                                .url("https://github.com/itobey/fddb-exporter"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("https://api.example.com")
                                .description("Production server")
                ));
    }
}

