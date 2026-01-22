package dev.itobey.adapter.api.fddb.exporter.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain entity for storing user settings and preferences.
 * Uses a singleton pattern with a fixed ID to support multi-device synchronization
 * without requiring user authentication.
 */
@Document(collection = "user_settings")
@Data
public class UserSettings {

    public static final String DEFAULT_SETTINGS_ID = "default-settings";

    @Id
    private String id = DEFAULT_SETTINGS_ID;

    // Correlation View Settings
    private List<String> correlationInclusionKeywords = new ArrayList<>();
    private List<String> correlationExclusionKeywords = new ArrayList<>();
    private String correlationOccurrenceDates;
    private LocalDate correlationStartDate;

}
