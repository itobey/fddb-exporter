package dev.itobey.adapter.api.fddb.exporter.repository;

import dev.itobey.adapter.api.fddb.exporter.domain.UserSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for managing {@link UserSettings} in MongoDB.
 * Only active when MongoDB persistence is enabled.
 */
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public interface UserSettingsRepository extends MongoRepository<UserSettings, String> {
}
