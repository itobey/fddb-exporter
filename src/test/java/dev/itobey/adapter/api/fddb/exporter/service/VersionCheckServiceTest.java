package dev.itobey.adapter.api.fddb.exporter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VersionCheckService.
 * These tests focus on the version comparison logic and the public API methods.
 * HTTP interactions are tested through integration tests or by examining real behavior.
 */
@ExtendWith(MockitoExtension.class)
class VersionCheckServiceTest {

    private VersionCheckService versionCheckService;

    @Mock
    private BuildProperties buildProperties;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // For ZonedDateTime support
        versionCheckService = new VersionCheckService(Optional.of(buildProperties), objectMapper);
    }

    @Test
    void getLatestVersionIfNewer_shouldReturnEmptyWhenNoCheckPerformed() {
        // when
        Optional<String> latestVersion = versionCheckService.getLatestVersionIfNewer();

        // then
        assertFalse(latestVersion.isPresent());
    }

    @Test
    void getReleaseUrl_shouldReturnEmptyWhenNoCheckPerformed() {
        // when
        Optional<String> releaseUrl = versionCheckService.getReleaseUrl();

        // then
        assertFalse(releaseUrl.isPresent());
    }

    @Test
    void checkForNewVersion_shouldHandleNoBuildProperties() {
        // given
        versionCheckService = new VersionCheckService(Optional.empty(), objectMapper);

        // when/then - should not throw exception
        assertDoesNotThrow(() -> versionCheckService.checkForNewVersion());
    }

    // Tests for the version comparison logic using reflection
    @Test
    void isNewerVersion_shouldDetectNewerVersion() {
        // when
        boolean result = invokeIsNewerVersion("1.7.0", "1.8.0");

        // then
        assertTrue(result);
    }

    @Test
    void isNewerVersion_shouldDetectOlderVersion() {
        // when
        boolean result = invokeIsNewerVersion("1.8.0", "1.7.0");

        // then
        assertFalse(result);
    }

    @Test
    void isNewerVersion_shouldHandleEqualVersions() {
        // when
        boolean result = invokeIsNewerVersion("1.8.0", "1.8.0");

        // then
        assertFalse(result);
    }

    @Test
    void isNewerVersion_shouldHandleVersionsWithVPrefix() {
        // when
        boolean result1 = invokeIsNewerVersion("v1.7.0", "v1.8.0");
        boolean result2 = invokeIsNewerVersion("1.7.0", "v1.8.0");
        boolean result3 = invokeIsNewerVersion("v1.7.0", "1.8.0");

        // then
        assertTrue(result1);
        assertTrue(result2);
        assertTrue(result3);
    }

    @Test
    void isNewerVersion_shouldConsiderSnapshotOlderThanStable() {
        // when
        boolean result = invokeIsNewerVersion("1.7.1-SNAPSHOT", "1.7.0");

        // then
        assertTrue(result, "SNAPSHOT version should be considered older than stable release");
    }

    @Test
    void isNewerVersion_shouldHandleMinorVersionIncrement() {
        // when
        boolean result = invokeIsNewerVersion("1.7.9", "1.8.0");

        // then
        assertTrue(result);
    }

    @Test
    void isNewerVersion_shouldHandlePatchVersionIncrement() {
        // when
        boolean result = invokeIsNewerVersion("1.8.0", "1.8.1");

        // then
        assertTrue(result);
    }

    @Test
    void isNewerVersion_shouldHandleMajorVersionIncrement() {
        // when
        boolean result = invokeIsNewerVersion("1.9.9", "2.0.0");

        // then
        assertTrue(result);
    }

    @Test
    void isNewerVersion_shouldHandleDifferentVersionLengths() {
        // when
        boolean result1 = invokeIsNewerVersion("1.7", "1.7.1");
        boolean result2 = invokeIsNewerVersion("1.7.0", "1.7");
        boolean result3 = invokeIsNewerVersion("1.7.0.0", "1.7.0.1");

        // then
        assertTrue(result1);
        assertFalse(result2);
        assertTrue(result3);
    }

    @Test
    void isNewerVersion_shouldHandleNonNumericSuffixes() {
        // when
        boolean result1 = invokeIsNewerVersion("1.7.0-beta", "1.8.0-beta");
        boolean result2 = invokeIsNewerVersion("1.7.0-rc1", "1.8.0-rc2");

        // then
        // The method strips non-numeric suffixes, so these should compare numerically
        assertTrue(result1);
        assertTrue(result2);
    }

    @Test
    void isNewerVersion_shouldHandleInvalidVersionStrings() {
        // when/then - should not throw exception
        assertDoesNotThrow(() -> invokeIsNewerVersion("invalid", "1.8.0"));
        assertDoesNotThrow(() -> invokeIsNewerVersion("1.8.0", "invalid"));
        assertDoesNotThrow(() -> invokeIsNewerVersion("", "1.8.0"));
    }

    /**
     * Helper method to invoke the private isNewerVersion method.
     */
    @SneakyThrows
    private boolean invokeIsNewerVersion(String currentVersion, String newVersion) {
        Method method = VersionCheckService.class.getDeclaredMethod("isNewerVersion", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(versionCheckService, currentVersion, newVersion);
    }

    /**
     * Test getCurrentVersion with build properties present.
     */
    @Test
    void getCurrentVersion_shouldReturnVersionFromBuildProperties() {
        // given
        when(buildProperties.getVersion()).thenReturn("1.8.0");

        // when
        String version = invokeGetCurrentVersion();

        // then
        assertEquals("1.8.0", version);
    }

    /**
     * Test getCurrentVersion with no build properties.
     */
    @Test
    void getCurrentVersion_shouldReturnDevWhenNoBuildProperties() {
        // given
        versionCheckService = new VersionCheckService(Optional.empty(), objectMapper);

        // when
        String version = invokeGetCurrentVersion();

        // then
        assertEquals("dev", version);
    }

    /**
     * Helper method to invoke the private getCurrentVersion method.
     */
    @SneakyThrows
    private String invokeGetCurrentVersion() {
        Method method = VersionCheckService.class.getDeclaredMethod("getCurrentVersion");
        method.setAccessible(true);
        return (String) method.invoke(versionCheckService);
    }

    /**
     * Test parseVersionPart method.
     */
    @Test
    void parseVersionPart_shouldParseNumericPart() {
        // when/then
        assertEquals(7, invokeParseVersionPart("7"));
        assertEquals(10, invokeParseVersionPart("10"));
        assertEquals(0, invokeParseVersionPart("0"));
    }

    @Test
    void parseVersionPart_shouldHandleNonNumericSuffix() {
        // when/then
        assertEquals(7, invokeParseVersionPart("7-beta"));
        assertEquals(1, invokeParseVersionPart("1-SNAPSHOT"));
        assertEquals(8, invokeParseVersionPart("8rc1"));
    }

    @Test
    void parseVersionPart_shouldHandleInvalidInput() {
        // when/then
        assertEquals(0, invokeParseVersionPart("invalid"));
        assertEquals(0, invokeParseVersionPart(""));
        assertEquals(0, invokeParseVersionPart("-123"));
    }

    /**
     * Helper method to invoke the private parseVersionPart method.
     */
    @SneakyThrows
    private int invokeParseVersionPart(String part) {
        Method method = VersionCheckService.class.getDeclaredMethod("parseVersionPart", String.class);
        method.setAccessible(true);
        return (int) method.invoke(versionCheckService, part);
    }
}

