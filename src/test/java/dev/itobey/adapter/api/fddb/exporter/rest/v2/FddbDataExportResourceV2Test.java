package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDate;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit test for v2 Export API.
 */
@ExtendWith(MockitoExtension.class)
@Tag("v2")
class FddbDataExportResourceV2Test {

    @Mock
    private FddbDataService fddbDataService;

    @InjectMocks
    private FddbDataExportResourceV2 fddbDataExportResourceV2;

    @Test
    void recentProgressFlushesBeforeTheNextDayAndCompletes() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(fddbDataService.exportForDaysBack(eq(7), eq(true), any())).thenAnswer(call -> {
            BiConsumer<LocalDate, Boolean> onDay = call.getArgument(2);
            onDay.accept(LocalDate.parse("2026-10-01"), true);
            assertTrue(response.isCommitted());
            assertThat(response.getContentAsString()).contains("2026-10-01").doesNotContain("2026-10-02");
            onDay.accept(LocalDate.parse("2026-10-02"), false);
            return new ExportResultDTO();
        });
        fddbDataExportResourceV2.exportRecentWithProgress(7, true, response);
        assertThat(response.getContentAsString().lines()).hasSize(3);
        assertThat(response.getContentAsString()).contains("\"complete\":true");
    }

    @Test
    void progressEndpointFlushesEachDayAndEndsWithCompletion() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        DateRangeDTO range = new DateRangeDTO("2026-10-01", "2026-10-02");
        when(fddbDataService.exportForTimerange(eq(range),
                any())).thenAnswer(call -> {
            BiConsumer<LocalDate, Boolean> onDay = call.getArgument(1);
            onDay.accept(LocalDate.parse("2026-10-01"), true);
            assertTrue(response.isCommitted());
            assertThat(response.getContentAsString())
                    .contains("2026-10-01").doesNotContain("2026-10-02").doesNotContain("\"complete\":true");
            onDay.accept(LocalDate.parse("2026-10-02"), false);
            return new ExportResultDTO();
        });
        fddbDataExportResourceV2.exportWithProgress(range, response);
        assertThat(response.getContentAsString().lines()).hasSize(3);
        assertThat(response.getContentAsString()).contains("\"complete\":true");
    }

    @Test
    void testExportForTimerange() {
        DateRangeDTO mockRequest = new DateRangeDTO();
        ExportResultDTO mockResult = new ExportResultDTO();
        when(fddbDataService.exportForTimerange(mockRequest)).thenReturn(mockResult);

        ResponseEntity<ExportResultDTO> response = fddbDataExportResourceV2.exportForTimerange(mockRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResult, response.getBody());
    }

    @Test
    void testExportForDaysBack() {
        int days = 7;
        boolean includeToday = true;
        ExportResultDTO mockResult = new ExportResultDTO();
        when(fddbDataService.exportForDaysBack(days, includeToday)).thenReturn(mockResult);

        ResponseEntity<ExportResultDTO> response = fddbDataExportResourceV2.exportForDaysBack(days, includeToday);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResult, response.getBody());
    }
}

