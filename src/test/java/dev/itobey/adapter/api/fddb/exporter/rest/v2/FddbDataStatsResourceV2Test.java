package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExtremeDirection;
import dev.itobey.adapter.api.fddb.exporter.dto.MacroSplitDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.WeekdayStatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

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

    @Test
    void testGetExtremeDays() {
        List<StatsDTO.DayStats> mockDays = List.of(
                StatsDTO.DayStats.builder().date(LocalDate.of(2024, 3, 1)).total(3200).build());
        when(fddbDataService.getExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 10, null, null))
                .thenReturn(mockDays);

        ResponseEntity<?> response = fddbDataStatsResourceV2.getExtremeDays(
                NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 10, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockDays, response.getBody());
    }

    @Test
    void testGetExtremeDays_InvalidDateRange() {
        String errorMessage = "The 'from' date cannot be after the 'to' date";
        LocalDate fromDate = LocalDate.of(2024, 2, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 1);
        when(fddbDataService.getExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 10, fromDate, toDate))
                .thenThrow(new IllegalArgumentException(errorMessage));

        ResponseEntity<?> response = fddbDataStatsResourceV2.getExtremeDays(
                NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 10, fromDate, toDate);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(errorMessage, response.getBody());
    }

    @Test
    void testGetTrend() {
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);
        List<TrendPointDTO> mockTrend = List.of(TrendPointDTO.builder().bucket("2024-W01").build());
        when(fddbDataService.getTrend(NutrientMetric.PROTEIN, fromDate, toDate, TrendGranularity.WEEK))
                .thenReturn(mockTrend);

        ResponseEntity<?> response = fddbDataStatsResourceV2.getTrend(
                NutrientMetric.PROTEIN, fromDate, toDate, TrendGranularity.WEEK);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockTrend, response.getBody());
    }

    @Test
    void testGetWeekdayBreakdown() {
        List<WeekdayStatsDTO> mockBreakdown = List.of(
                WeekdayStatsDTO.builder().dayOfWeek(DayOfWeek.MONDAY).dayCount(4).build());
        when(fddbDataService.getWeekdayBreakdown(null, null)).thenReturn(mockBreakdown);

        ResponseEntity<?> response = fddbDataStatsResourceV2.getWeekdayBreakdown(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockBreakdown, response.getBody());
    }

    @Test
    void testGetMacroSplit() {
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);
        MacroSplitDTO mockSplit = MacroSplitDTO.builder().fatPercentage(34.5).build();
        when(fddbDataService.getMacroSplit(fromDate, toDate)).thenReturn(mockSplit);

        ResponseEntity<?> response = fddbDataStatsResourceV2.getMacroSplit(fromDate, toDate);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockSplit, response.getBody());
    }

    @Test
    void testGetMissingDays() {
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);
        List<LocalDate> mockMissing = List.of(LocalDate.of(2024, 1, 5));
        when(fddbDataService.getMissingDays(fromDate, toDate)).thenReturn(mockMissing);

        ResponseEntity<?> response = fddbDataStatsResourceV2.getMissingDays(fromDate, toDate);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockMissing, response.getBody());
    }
}

