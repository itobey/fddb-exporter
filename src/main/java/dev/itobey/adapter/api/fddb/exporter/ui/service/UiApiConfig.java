package dev.itobey.adapter.api.fddb.exporter.ui.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Shared HTTP setup for the Vaadin UI's REST clients.
 */
@Configuration
public class UiApiConfig {

    /**
     * One {@link RestTemplate} for all UI clients, with timeouts.
     * <p>
     * The UI calls the application's own REST API, so a blocking call here occupies a Vaadin request
     * thread while waiting for another thread from the same bounded Tomcat pool. Without an upper bound
     * on that wait, a saturated pool turns into a deadlock rather than a slow page.
     */
    @Bean("uiRestTemplate")
    public RestTemplate uiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return new RestTemplate(factory);
    }
}
