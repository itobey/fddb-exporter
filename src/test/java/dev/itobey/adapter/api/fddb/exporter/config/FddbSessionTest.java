package dev.itobey.adapter.api.fddb.exporter.config;

import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbSessionTest {

    private static final String BASE_URL = "https://fddb.info";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private FddbExporterProperties properties;
    @Mock
    private RestTemplate restTemplate;

    private FddbSession fddbSession;

    @BeforeEach
    void setUp() {
        fddbSession = new FddbSession(properties, restTemplate);
    }

    @Test
    void getCookie_shouldLogInOnlyOnce_whenCalledRepeatedly() {
        // given
        stubLoginReturning("session-one");

        // when
        String first = fddbSession.getCookie(BASE_URL);
        String second = fddbSession.getCookie(BASE_URL);

        // then
        assertEquals("session-one", first);
        assertEquals("session-one", second);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void getCookie_shouldLogInAgain_afterInvalidation() {
        // given: the cached session expired on fddb.info's side
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(loginResponse("session-one"))
                .thenReturn(loginResponse("session-two"));

        // when
        String first = fddbSession.getCookie(BASE_URL);
        fddbSession.invalidate();
        String second = fddbSession.getCookie(BASE_URL);

        // then
        assertEquals("session-one", first);
        assertEquals("session-two", second);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void getCookie_shouldPerformExactlyOneLogin_whenCalledConcurrently() throws Exception {
        // given: the scheduler, a REST request and the health indicator can all arrive at once
        int threads = 8;
        stubLoginReturning("session-one");
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<String>> results = IntStream.range(0, threads)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return fddbSession.getCookie(BASE_URL);
                    }))
                    .toList();

            // when
            start.countDown();

            // then
            for (Future<String> result : results) {
                assertEquals("session-one", result.get(10, TimeUnit.SECONDS));
            }
        }
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void getCookie_shouldFail_whenTheLoginResponseCarriesNoSessionCookie() {
        // given: wrong credentials get the login form back without a session cookie
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", new HttpHeaders(), HttpStatus.OK));

        // when / then
        assertThrows(AuthenticationException.class, () -> fddbSession.getCookie(BASE_URL));
    }

    private void stubLoginReturning(String cookieValue) {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(loginResponse(cookieValue));
    }

    private ResponseEntity<String> loginResponse(String cookieValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, "fddb=" + cookieValue + "; Path=/; HttpOnly");
        return new ResponseEntity<>("", headers, HttpStatus.OK);
    }
}
