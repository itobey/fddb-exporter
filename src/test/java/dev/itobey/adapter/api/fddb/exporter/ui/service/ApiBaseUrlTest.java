package dev.itobey.adapter.api.fddb.exporter.ui.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiBaseUrlTest {

    @Test
    void get_shouldUseTheConfiguredPort() {
        assertEquals("http://localhost:9090", new ApiBaseUrl(9090, "", false).get());
    }

    @Test
    void get_shouldIncludeTheContextPath() {
        assertEquals("http://localhost:8080/fddb", new ApiBaseUrl(8080, "/fddb", false).get());
    }

    @Test
    void get_shouldNormalizeAContextPathWithoutLeadingOrWithTrailingSlash() {
        assertEquals("http://localhost:8080/fddb", new ApiBaseUrl(8080, "fddb/", false).get());
        assertEquals("http://localhost:8080", new ApiBaseUrl(8080, "/", false).get());
    }

    @Test
    void get_shouldSwitchToHttps_whenSslIsEnabled() {
        assertEquals("https://localhost:8443", new ApiBaseUrl(8443, "", true).get());
    }

    @Test
    void get_shouldUseTheActualPort_onceTheWebServerHasStarted() {
        // given: server.port=0 picks a random port, which only the running server knows
        ApiBaseUrl apiBaseUrl = new ApiBaseUrl(0, "", false);
        WebServer webServer = mock(WebServer.class);
        when(webServer.getPort()).thenReturn(54321);
        WebServerInitializedEvent event = mock(WebServerInitializedEvent.class);
        when(event.getWebServer()).thenReturn(webServer);

        // when
        apiBaseUrl.onApplicationEvent(event);

        // then
        assertEquals("http://localhost:54321", apiBaseUrl.get());
    }
}
