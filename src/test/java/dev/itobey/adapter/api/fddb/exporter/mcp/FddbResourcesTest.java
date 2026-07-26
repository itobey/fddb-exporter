package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FddbResourcesTest {

    @InjectMocks
    private FddbResources fddbResources;
    @Mock
    private FddbDataService fddbDataService;
    @Mock
    private FddbSchemaTools fddbSchemaTools;

    @Test
    void stats_shouldSerializeDatesAsIsoStringsRatherThanArrays() {
        // given
        when(fddbDataService.getStats()).thenReturn(StatsDTO.builder()
                .amountEntries(3)
                .firstEntryDate(LocalDate.of(2024, 1, 1))
                .build());

        // when
        String json = fddbResources.stats();

        // then the app's own ObjectMapper would write [2024,1,1] here, which no client can read
        assertTrue(json.contains("\"firstEntryDate\":\"2024-01-01\""), json);
        assertTrue(json.contains("\"amountEntries\":3"), json);
    }

    @Test
    void day_shouldReturnTheEntryWithoutItsDatabaseId() {
        // given
        FddbDataDTO entry = new FddbDataDTO();
        entry.setId("507f1f77bcf86cd799439011");
        entry.setDate(LocalDate.of(2024, 12, 22));
        entry.setTotalCalories(2000);
        when(fddbDataService.findByDate("2024-12-22")).thenReturn(Optional.of(entry));

        // when
        String json = fddbResources.day("2024-12-22");

        // then
        assertTrue(json.contains("\"found\":true"), json);
        assertTrue(json.contains("\"date\":\"2024-12-22\""), json);
        assertFalse(json.contains("507f1f77bcf86cd799439011"), json);
    }

    @Test
    void day_shouldResolveRelativeDatesLikeTheToolsDo() {
        // given
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(fddbDataService.findByDate(yesterday.toString())).thenReturn(Optional.empty());

        // when
        String json = fddbResources.day("yesterday");

        // then
        assertTrue(json.contains("\"found\":false"), json);
        assertTrue(json.contains("No entry was logged for " + yesterday), json);
    }

    @Test
    void day_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbResources.day("last tuesday"));
    }

    @Test
    void schema_shouldReturnTheSameTextAsTheTool() {
        // given
        when(fddbSchemaTools.getDataSchema()).thenReturn("# FDDB-Exporter data model");

        // when / then
        assertEquals("# FDDB-Exporter data model", fddbResources.schema());
    }
}
