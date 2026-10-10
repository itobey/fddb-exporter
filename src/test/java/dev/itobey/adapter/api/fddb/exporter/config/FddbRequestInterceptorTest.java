package dev.itobey.adapter.api.fddb.exporter.config;

import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbRequestInterceptorTest {

    private static final String FDDB_URL = "https://fddb.info";

    @Mock
    private FddbSession fddbSession;

    private FddbRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        FddbExporterProperties.Fddb fddb = new FddbExporterProperties.Fddb();
        fddb.setUrl(FDDB_URL);
        fddb.setUsername("user");
        fddb.setPassword("secret");
        FddbExporterProperties properties = new FddbExporterProperties();
        properties.setFddb(fddb);
        interceptor = new FddbRequestInterceptor(properties, fddbSession);
    }

    @Test
    void apply_shouldSendTheSessionCookieAndBasicAuth() {
        // given
        when(fddbSession.getCookie(FDDB_URL)).thenReturn("session-one");
        RequestTemplate template = templateFor(FDDB_URL);

        // when
        interceptor.apply(template);

        // then
        assertEquals("fddb=session-one", template.headers().get("Cookie").iterator().next());
        assertEquals("Basic dXNlcjpzZWNyZXQ=", template.headers().get("Authorization").iterator().next());
    }

    @Test
    void apply_shouldResolveTheCookieOnEveryRequest_soAnInvalidatedSessionIsPickedUp() {
        // given: caching happens in FddbSession, not here - the interceptor must not hold a copy
        when(fddbSession.getCookie(FDDB_URL)).thenReturn("session-one", "session-two");

        // when
        RequestTemplate first = templateFor(FDDB_URL);
        interceptor.apply(first);
        RequestTemplate second = templateFor(FDDB_URL);
        interceptor.apply(second);

        // then
        assertEquals("fddb=session-one", first.headers().get("Cookie").iterator().next());
        assertEquals("fddb=session-two", second.headers().get("Cookie").iterator().next());
        verify(fddbSession, times(2)).getCookie(FDDB_URL);
    }

    @Test
    void apply_shouldDoNothing_forATargetThatIsNotFddb() {
        // given
        RequestTemplate template = templateFor("https://telemetry.itobey.dev");

        // when
        interceptor.apply(template);

        // then
        assertNull(template.headers().get("Cookie"));
        verifyNoInteractions(fddbSession);
    }

    private RequestTemplate templateFor(String url) {
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(new Target.HardCodedTarget<>(Object.class, url));
        return template;
    }
}
