package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresInfluxDb;
import dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb;
import dev.itobey.adapter.api.fddb.exporter.service.DataMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 REST API for data migration operations.
 * <p>
 * Provides endpoints for:
 * - Migrating data from MongoDB to InfluxDB
 * <p>
 * The API endpoints are mapped to the "/api/v2/migration" path.
 *
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v2/migration")
@Slf4j
@Validated
@Tag(name = "FDDB Data Migration", description = "Data migration operations between storage backends")
public class FddbDataMigrationResourceV2 {

    @Autowired(required = false)
    private DataMigrationService dataMigrationService;

    /**
     * Migrate all MongoDB entries to InfluxDB.
     *
     * @return the number of entries migrated
     */
    @Operation(summary = "Migrate data to InfluxDB", description = "Migrate all MongoDB entries to InfluxDB")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Migration completed successfully", content = @Content),
            @ApiResponse(responseCode = "503", description = "MongoDB or InfluxDB not available", content = @Content)
    })
    @PostMapping("/toInfluxDb")
    @RequiresMongoDb
    @RequiresInfluxDb
    public ResponseEntity<String> migrateMongoDbEntriesToInfluxDb() {
        log.info("V2: Starting migration from MongoDB to InfluxDB");
        int amountEntries = dataMigrationService.migrateMongoDbEntriesToInfluxDb();
        log.info("V2: Migration completed. Migrated {} entries", amountEntries);
        return ResponseEntity.ok("Migrated " + amountEntries + " entries to InfluxDB");
    }
}
