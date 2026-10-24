package dev.itobey.adapter.api.fddb.exporter.ui.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** On-demand, shared GitHub fetch; opening release notes never blocks the Vaadin session. */
@Service
public class ChangelogClient {
    public static final String SOURCE_URL = "https://github.com/itobey/fddb-exporter/blob/master/CHANGELOG.md";
    private static final URI RAW_URL = URI.create(
            "https://raw.githubusercontent.com/itobey/fddb-exporter/master/CHANGELOG.md");
    private final HttpClient http;
    private final Clock clock;
    private volatile Snapshot cached;
    private volatile Instant refreshAfter = Instant.MIN;
    private CompletableFuture<Snapshot> inFlight;

    public ChangelogClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), Clock.systemUTC());
    }

    ChangelogClient(HttpClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
    }

    public synchronized CompletableFuture<Snapshot> load() {
        if (cached != null && clock.instant().isBefore(refreshAfter)) {
            return CompletableFuture.completedFuture(cached);
        }
        if (inFlight != null && !inFlight.isDone()) {
            return inFlight;
        }
        HttpRequest request = HttpRequest.newBuilder(RAW_URL)
                .timeout(Duration.ofSeconds(10)).header("Accept", "text/plain").GET().build();
        inFlight = http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("GitHub returned HTTP " + response.statusCode());
                    }
                    List<Release> releases = parse(response.body());
                    if (releases.isEmpty()) {
                        throw new IllegalStateException("No version headings in the changelog");
                    }
                    return new Snapshot(releases, clock.instant(), false);
                }).handle((snapshot, failure) -> {
                    if (failure == null) {
                        cached = snapshot;
                        refreshAfter = clock.instant().plus(Duration.ofHours(1));
                        return snapshot;
                    }
                    Snapshot previous = cached;
                    if (previous != null) {
                        cached = new Snapshot(previous.releases(), previous.fetchedAt(), true);
                        refreshAfter = clock.instant().plus(Duration.ofMinutes(1));
                        return cached;
                    }
                    throw new CompletionException(failure);
                });
        return inFlight;
    }

    /** Split level-two release headings, preserving Markdown and ignoring fenced code examples. */
    static List<Release> parse(String markdown) {
        List<Release> releases = new ArrayList<>();
        String version = null;
        StringBuilder notes = new StringBuilder();
        char fence = 0;
        int fenceLength = 0;
        for (String line : markdown.lines().toList()) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                char marker = trimmed.charAt(0);
                int length = 0;
                while (length < trimmed.length() && trimmed.charAt(length) == marker) {
                    length++;
                }
                if (fence == 0) {
                    fence = marker;
                    fenceLength = length;
                } else if (fence == marker && length >= fenceLength && trimmed.substring(length).isBlank()) {
                    fence = 0;
                }
            }
            if (fence == 0 && line.startsWith("## ")) {
                if (version != null) {
                    releases.add(new Release(version, notes.toString().strip()));
                }
                version = line.substring(3).strip();
                notes.setLength(0);
            } else if (version != null) {
                notes.append(line).append('\n');
            }
        }
        if (version != null) {
            releases.add(new Release(version, notes.toString().strip()));
        }
        return List.copyOf(releases);
    }

    public record Release(String version, String markdown) { }

    public record Snapshot(List<Release> releases, Instant fetchedAt, boolean stale) { }
}
