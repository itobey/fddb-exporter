package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.service.DataMigrationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit test for v2 Migration API.
 */
@ExtendWith(MockitoExtension.class)
@Tag("v2")
class FddbDataMigrationResourceV2Test {

    @Mock
    private DataMigrationService dataMigrationService;

    @InjectMocks
    private FddbDataMigrationResourceV2 fddbDataMigrationResourceV2;

    @Test
    void testMigrateMongoDbEntriesToInfluxDb() {
        int expectedMigratedCount = 42;
        when(dataMigrationService.migrateMongoDbEntriesToInfluxDb()).thenReturn(expectedMigratedCount);

        ResponseEntity<String> response = fddbDataMigrationResourceV2.migrateMongoDbEntriesToInfluxDb();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(String.valueOf(expectedMigratedCount)));
        assertTrue(response.getBody().contains("Migrated"));
        assertTrue(response.getBody().contains("entries to InfluxDB"));
    }
}

