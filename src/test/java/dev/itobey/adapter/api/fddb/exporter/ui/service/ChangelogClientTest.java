package dev.itobey.adapter.api.fddb.exporter.ui.service;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChangelogClientTest {
    private static final String NOTES = "# Changelog\n\n## 2.6.2\n\n### Fixed\n\n- **Names** stay intact.\n\n## 1.0.0\n\n- First release.\n";

    @Test
    void keepsOlderVersionsAndMarkdownButDoesNotSplitHeadingsInsideCodeFences() {
        var releases = ChangelogClient.parse(NOTES + "\n```md\n## Not a release\n```\n");
        assertThat(releases).extracting(ChangelogClient.Release::version).containsExactly("2.6.2", "1.0.0");
        assertThat(releases.getFirst().markdown()).contains("### Fixed", "**Names**");
        assertThat(releases.getLast().markdown()).contains("## Not a release");
        assertThat(ChangelogClient.parse("# Empty changelog")).isEmpty();
    }

    @Test
    void concurrentReadersShareOneFetchAndSuccessfulNotesAreCached() {
        HttpClient http = mock(HttpClient.class);
        CompletableFuture<HttpResponse<String>> response = new CompletableFuture<>();
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client = new ChangelogClient(http, Clock.systemUTC());
        var first = client.load();
        assertThat(first.isDone()).isFalse();
        assertThat(client.load()).isSameAs(first);
        response.complete(response(200, NOTES));
        assertThat(first.join().releases()).hasSize(2);
        assertThat(client.load().join()).isEqualTo(first.join());
        verify(http, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void upstreamFailureUsesPreviouslyFetchedNotesAndRetriesAfterShortBackoff() {
        HttpClient http = mock(HttpClient.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-10-24T10:00:00Z");
        when(clock.instant()).thenReturn(now);
        var success = response(200, NOTES);
        var unavailable = response(503, "Unavailable");
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(success))
                .thenReturn(CompletableFuture.completedFuture(unavailable))
                .thenReturn(CompletableFuture.completedFuture(success));
        var client = new ChangelogClient(http, clock);
        var original = client.load().join();
        when(clock.instant()).thenReturn(now.plusSeconds(3601));
        var stale = client.load().join();
        assertThat(stale.stale()).isTrue();
        assertThat(stale.fetchedAt()).isEqualTo(original.fetchedAt());
        assertThat(stale.releases()).isEqualTo(original.releases());
        assertThat(client.load().join().stale()).isTrue();
        when(clock.instant()).thenReturn(now.plusSeconds(3662));
        assertThat(client.load().join().stale()).isFalse();
        verify(http, times(3)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void failureWithoutCacheAndMalformedContentAreRetryable() {
        HttpClient http = mock(HttpClient.class);
        var malformed = response(200, "<html>Error</html>");
        var success = response(200, NOTES);
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Offline")))
                .thenReturn(CompletableFuture.completedFuture(malformed))
                .thenReturn(CompletableFuture.completedFuture(success));
        var client = new ChangelogClient(http, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThatThrownBy(() -> client.load().join()).hasRootCauseMessage("Offline");
        assertThatThrownBy(() -> client.load().join()).hasRootCauseMessage("No version headings in the changelog");
        assertThat(client.load().join().releases()).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
