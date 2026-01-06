package dev.itobey.adapter.api.fddb.exporter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itobey.adapter.api.fddb.exporter.dto.GitHubReleaseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service to check for new versions of the application from GitHub releases.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionCheckService {

    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/itobey/fddb-exporter/releases/latest";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Optional<BuildProperties> buildProperties;
    private final ObjectMapper objectMapper;

    private final AtomicReference<String> latestVersion = new AtomicReference<>();
    private final AtomicReference<String> releaseUrl = new AtomicReference<>();

    /**
     * Check for a new version on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        checkForNewVersion();
    }

    /**
     * Check for a new version and log if one is available.
     * This method is designed to be called periodically (e.g., daily).
     */
    public void checkForNewVersion() {
        try {
            log.debug("Checking for new version from GitHub releases...");

            GitHubReleaseDTO latestRelease = fetchLatestRelease();

            if (latestRelease == null || latestRelease.isDraft() || latestRelease.isPrerelease()) {
                log.debug("No stable release found or release is draft/prerelease");
                return;
            }

            String latestVersionTag = latestRelease.getTagName();
            String currentVersion = getCurrentVersion();

            // Store the latest version and URL for UI access
            latestVersion.set(latestVersionTag);
            releaseUrl.set(latestRelease.getHtmlUrl());

            if (isNewerVersion(currentVersion, latestVersionTag)) {
                String message = String.format(
                        "New version available! Current: %s, Latest: %s - %s",
                        currentVersion,
                        latestVersionTag,
                        latestRelease.getHtmlUrl()
                );
                log.info(message);
            } else {
                log.debug("Current version {} is up to date (latest: {})", currentVersion, latestVersionTag);
            }

        } catch (Exception e) {
            log.warn("Failed to check for new version: {}", e.getMessage());
        }
    }

    /**
     * Get the latest available version if a newer version is available.
     *
     * @return Optional containing the latest version tag, or empty if no update is available
     */
    public Optional<String> getLatestVersionIfNewer() {
        String latest = latestVersion.get();
        String current = getCurrentVersion();

        if (latest != null && isNewerVersion(current, latest)) {
            return Optional.of(latest);
        }

        return Optional.empty();
    }

    /**
     * Get the URL to the latest release on GitHub.
     *
     * @return Optional containing the release URL, or empty if not available
     */
    public Optional<String> getReleaseUrl() {
        return Optional.ofNullable(releaseUrl.get());
    }

    private GitHubReleaseDTO fetchLatestRelease() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_RELEASES_API))
                .header("Accept", "application/vnd.github.v3+json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), GitHubReleaseDTO.class);
        } else {
            log.warn("GitHub API returned status code: {}", response.statusCode());
            return null;
        }
    }

    private String getCurrentVersion() {
        return buildProperties
                .map(BuildProperties::getVersion)
                .orElse("dev");
    }

    /**
     * Compare two semantic version strings to determine if the new version is newer.
     * Handles versions with or without 'v' prefix and '-SNAPSHOT' suffix.
     * SNAPSHOT versions are always considered older than stable releases.
     * For example: 1.7.1-SNAPSHOT is older than 1.7.0 stable.
     *
     * @param currentVersion the current version
     * @param newVersion     the new version to compare
     * @return true if newVersion is newer than currentVersion
     */
    private boolean isNewerVersion(String currentVersion, String newVersion) {
        try {
            // Remove 'v' prefix if present
            String current = currentVersion.replaceFirst("^v", "");
            String latest = newVersion.replaceFirst("^v", "");

            // Check if versions are SNAPSHOT
            boolean currentIsSnapshot = current.endsWith("-SNAPSHOT");
            boolean latestIsSnapshot = latest.endsWith("-SNAPSHOT");

            // If new version is SNAPSHOT, it's never considered newer
            if (latestIsSnapshot) {
                return false;
            }

            // If current is SNAPSHOT but latest is stable, latest is always newer
            if (currentIsSnapshot) {
                return true;
            }

            // Split version numbers
            String[] currentParts = current.split("\\.");
            String[] latestParts = latest.split("\\.");

            // Compare each part
            int maxLength = Math.max(currentParts.length, latestParts.length);
            for (int i = 0; i < maxLength; i++) {
                int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
                int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;

                if (latestPart > currentPart) {
                    return true;
                } else if (latestPart < currentPart) {
                    return false;
                }
            }

            return false; // Versions are equal
        } catch (Exception exception) {
            log.warn("Error comparing versions: {} vs {}", currentVersion, newVersion, exception);
            return false;
        }
    }

    private int parseVersionPart(String part) {
        try {
            // Remove any non-numeric suffix (like -beta, -rc1, etc.)
            String numericPart = part.replaceAll("[^0-9].*$", "");
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

