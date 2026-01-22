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
        } catch (Exception exception) {
            log.warn("Error loading settings from MongoDB, returning defaults: {}", exception.getMessage());
            return new UserSettings();
        }
    }

    /**
     * Saves the user settings to MongoDB.
     *
     * @param settings the settings to save
     */
    public void saveSettings(UserSettings settings) {
        try {
            settings.setId(UserSettings.DEFAULT_SETTINGS_ID);
            userSettingsRepository.save(settings);
            log.info("Settings saved successfully");
        } catch (Exception exception) {
            log.error("Error saving settings to MongoDB: {}", exception.getMessage(), exception);
        }
    }

    /**
     * Creates and persists default settings.
     *
     * @return newly created default settings
     */
    private UserSettings createDefaultSettings() {
        UserSettings defaultSettings = new UserSettings();
        defaultSettings.setId(UserSettings.DEFAULT_SETTINGS_ID);
        try {
            return userSettingsRepository.save(defaultSettings);
        } catch (Exception exception) {
            log.warn("Error creating default settings: {}", exception.getMessage());
            return defaultSettings;
        }
    }
}
