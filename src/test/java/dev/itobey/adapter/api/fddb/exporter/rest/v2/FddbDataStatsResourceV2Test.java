package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit test for v2 Stats API.
 */
@ExtendWith(MockitoExtension.class)
@Tag("v2")
class FddbDataStatsResourceV2Test {

    @Mock
    private FddbDataService fddbDataService;

    @InjectMocks
    private FddbDataStatsResourceV2 fddbDataStatsResourceV2;

    @Test
    void testGetStats() {
        StatsDTO mockStats = new StatsDTO();
        when(fddbDataService.getStats()).thenReturn(mockStats);

        ResponseEntity<StatsDTO> response = fddbDataStatsResourceV2.getStats();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockStats, response.getBody());
    }

    @Test
    void testGetRollingAverages_Success() {
        DateRangeDTO dateRangeDTO = DateRangeDTO.builder()
                .fromDate("2023-01-01")
                .toDate("2023-01-31")
                .build();
        RollingAveragesDTO mockAverages = new RollingAveragesDTO();
        when(fddbDataService.getRollingAverages(dateRangeDTO)).thenReturn(mockAverages);

        ResponseEntity<?> response = fddbDataStatsResourceV2.getRollingAverages(dateRangeDTO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockAverages, response.getBody());
    }

    @Test
    void testGetRollingAverages_InvalidDateRange() {
        DateRangeDTO dateRangeDTO = DateRangeDTO.builder()
                .fromDate("2023-01-31")
                .toDate("2023-01-01")
                .build();
        String errorMessage = "From date must be before to date";
        when(fddbDataService.getRollingAverages(dateRangeDTO))
                .thenThrow(new IllegalArgumentException(errorMessage));

        ResponseEntity<?> response = fddbDataStatsResourceV2.getRollingAverages(dateRangeDTO);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, response.getBody());
    }
}

