package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.UserSettings;
import dev.itobey.adapter.api.fddb.exporter.repository.UserSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for managing user settings and preferences.
 * Provides methods to get and save settings with automatic fallback to defaults.
 * Uses a singleton document approach for multi-device synchronization without user authentication.
 */
@Service
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
@Slf4j
public class UserSettingsService {

    @Autowired(required = false)
    private UserSettingsRepository userSettingsRepository;

    /**
     * Gets the current user settings.
     * Returns existing settings or creates a new default settings document if none exists.
     * <p>
     * A read failure falls back to in-memory defaults on purpose: settings are decoration around every
     * view, so an unreachable MongoDB should still render the page rather than break it. The fallback is
     * never persisted by this method, so nothing is overwritten — but it is logged at warn with the
     * exception, because a user seeing empty presets needs a trace explaining why.
     *
     * @return UserSettings object
     */
    public UserSettings getSettings() {
        try {
            Optional<UserSettings> settings = userSettingsRepository.findById(UserSettings.DEFAULT_SETTINGS_ID);
            if (settings.isPresent()) {
                log.debug("Loaded user settings from database");
                return settings.get();
            } else {
                log.info("No settings found, creating default settings");
                return createDefaultSettings();
            }
        } catch (RuntimeException exception) {
            log.warn("Error loading settings from MongoDB, returning defaults: {}", exception.getMessage(), exception);
            return new UserSettings();
        }
    }

    /**
     * Saves the user settings to MongoDB.
     * Failures are logged and rethrown so callers can tell the user the write did not happen instead of
     * reporting success over silently lost data.
     *
     * @param settings the settings to save
     * @throws RuntimeException if the settings could not be persisted
     */
    public void saveSettings(UserSettings settings) {
        try {
            settings.setId(UserSettings.DEFAULT_SETTINGS_ID);
            userSettingsRepository.save(settings);
            log.info("Settings saved successfully");
        } catch (RuntimeException exception) {
            log.error("Error saving settings to MongoDB: {}", exception.getMessage(), exception);
            throw exception;
        }
    }

    /**
     * Creates and persists default settings.
     * A failure here propagates to {@link #getSettings()}, which logs it and falls back to unpersisted
     * defaults — swallowing it a second time here would only hide the cause.
     *
     * @return newly created default settings
     */
    private UserSettings createDefaultSettings() {
        UserSettings defaultSettings = new UserSettings();
        defaultSettings.setId(UserSettings.DEFAULT_SETTINGS_ID);
        return userSettingsRepository.save(defaultSettings);
    }
}
