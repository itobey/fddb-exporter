package dev.itobey.adapter.api.fddb.exporter.ui.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * The base URL the Vaadin UI's REST clients call the application's own API on.
 * <p>
 * This used to be the literal {@code http://localhost:8080} in four separate clients, so setting
 * {@code server.port} or a {@code server.servlet.context-path} broke every view with nothing in a config
 * file to explain why. The port is read from the running web server where possible, which also covers
 * {@code server.port=0}.
 */
@Component
public class ApiBaseUrl implements ApplicationListener<WebServerInitializedEvent> {

    private final String scheme;
    private final String contextPath;
    private volatile int port;

    public ApiBaseUrl(@Value("${server.port:8080}") int configuredPort,
                      @Value("${server.servlet.context-path:}") String contextPath,
                      @Value("${server.ssl.enabled:false}") boolean sslEnabled) {
        this.port = configuredPort;
        this.contextPath = normalize(contextPath);
        this.scheme = sslEnabled ? "https" : "http";
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    /**
     * @return the base URL to prefix an {@code /api/v2/...} path with, without a trailing slash
     */
    public String get() {
        return scheme + "://localhost:" + port + contextPath;
    }

    private static String normalize(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        String trimmed = contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath;
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
