package dev.itobey.adapter.api.fddb.exporter.config;

import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Holds the fddb.info session cookie shared by every request the Feign client makes.
 * <p>
 * The cookie is obtained lazily on the first request and kept until something tells us it is no longer
 * valid - see {@link #invalidate()}. Without that invalidation an expired session would make every
 * subsequent request come back logged out and the application would blame the user's credentials until
 * someone restarted it.
 */
@Component
@Slf4j
public class FddbSession {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final FddbExporterProperties properties;
    private final RestTemplate restTemplate;

    /**
     * Guarded so that concurrent callers - the scheduler, a REST request and the health indicator can all
     * arrive at once - perform exactly one login between them instead of one each.
     */
    private volatile String fddbCookie;

    @Autowired
    public FddbSession(FddbExporterProperties properties) {
        this(properties, buildRestTemplate());
    }

    /**
     * Test-only constructor taking a pre-configured {@link RestTemplate}.
     */
    FddbSession(FddbExporterProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * Returns the current session cookie, logging in first if there is none.
     *
     * @param baseUrl the fddb.info base URL to log in against
     * @return the value of the {@code fddb} cookie
     * @throws AuthenticationException if the login response carries no session cookie
     */
    public String getCookie(String baseUrl) {
        String cookie = fddbCookie;
        if (cookie != null) {
            return cookie;
        }
        synchronized (this) {
            if (fddbCookie == null) {
                fddbCookie = login(baseUrl);
            }
            return fddbCookie;
        }
    }

    /**
     * Drops the cached cookie so the next request logs in again.
     * <p>
     * Called when a response comes back logged out despite a cookie being sent, which means the session
     * expired rather than that the credentials are wrong.
     */
    public void invalidate() {
        synchronized (this) {
            fddbCookie = null;
        }
    }

    private String login(String baseUrl) {
        log.debug("logging in to FDDB");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("loginemailorusername", properties.getFddb().getUsername());
        map.add("loginpassword", properties.getFddb().getPassword());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/db/i18n/account/?lang=de&action=login",
                request,
                String.class
        );

        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("fddb=")) {
                    return cookie.split(";")[0].substring(5);
                }
            }
        }
        throw new AuthenticationException("Login to FDDB not successful, please check credentials");
    }

    /**
     * Built once, with timeouts: a hung fddb.info would otherwise wedge the calling thread - the
     * scheduler, a Tomcat request thread or a Vaadin UI thread - indefinitely.
     */
    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }
}
