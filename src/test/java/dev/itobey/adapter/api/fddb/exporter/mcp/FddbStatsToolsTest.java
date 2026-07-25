package dev.itobey.adapter.api.fddb.exporter.mcp;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.RollingAveragesDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbStatsToolsTest {

    @InjectMocks
    private FddbStatsTools fddbStatsTools;
    @Mock
    private FddbDataService fddbDataService;

    @Test
    void getStats_shouldDelegateToTheService() {
        // given
        StatsDTO stats = StatsDTO.builder().amountEntries(365).build();
        when(fddbDataService.getStats()).thenReturn(stats);

        // when
        StatsDTO result = fddbStatsTools.getStats();

        // then
        assertSame(stats, result);
    }

    @Test
    void getAverages_shouldPassTheResolvedRangeToTheService() {
        // given
        RollingAveragesDTO averages = RollingAveragesDTO.builder().build();
        when(fddbDataService.getRollingAverages(any(DateRangeDTO.class))).thenReturn(averages);

        // when
        RollingAveragesDTO result = fddbStatsTools.getAverages("2024-01-01", "2024-01-31");

        // then
        assertSame(averages, result);
        ArgumentCaptor<DateRangeDTO> range = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService).getRollingAverages(range.capture());
        assertEquals("2024-01-01", range.getValue().getFromDate());
        assertEquals("2024-01-31", range.getValue().getToDate());
    }

    @Test
    void getAverages_shouldResolveRelativeDates() {
        // given
        LocalDate today = LocalDate.now();
        when(fddbDataService.getRollingAverages(any(DateRangeDTO.class)))
                .thenReturn(RollingAveragesDTO.builder().build());

        // when
        fddbStatsTools.getAverages("6_days_ago", "today");

        // then
        ArgumentCaptor<DateRangeDTO> range = ArgumentCaptor.forClass(DateRangeDTO.class);
        verify(fddbDataService).getRollingAverages(range.capture());
        assertEquals(today.minusDays(6).toString(), range.getValue().getFromDate());
        assertEquals(today.toString(), range.getValue().getToDate());
    }

    @Test
    void getAverages_shouldRejectAnUnparseableDate() {
        // when / then
        assertThrows(DateTimeException.class, () -> fddbStatsTools.getAverages("beginning of the year", "today"));
        verifyNoInteractions(fddbDataService);
    }
}
