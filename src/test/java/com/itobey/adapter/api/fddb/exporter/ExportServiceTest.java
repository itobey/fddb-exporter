package com.itobey.adapter.api.fddb.exporter;

import com.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import com.itobey.adapter.api.fddb.exporter.service.ExportService;
import com.itobey.adapter.api.fddb.exporter.service.HtmlParser;
import com.itobey.adapter.api.fddb.exporter.service.PersistenceService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Test for {@link ExportService}
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @InjectMocks
    ExportService exportService;

    @Mock
    FddbAdapter fddbAdapter;
    @Mock
    HtmlParser htmlParser;
    @Mock
    PersistenceService persistenceService;

    Timeframe timeframe;
    String fddbResponse;
    FddbData fddbData;
    FddbData existingEntry;

    @BeforeEach
    public void setUp() {
        timeframe = Timeframe.builder().from(123).to(456).build();
        fddbResponse = "<html>something</html>";
        Date date = new Date(2021, 02, 01);
        fddbData = createFddbData(date);
        existingEntry = createFddbData(date);
        existingEntry.setCarbs(100);
        existingEntry.setId(123L);
    }

    @Test
    @SneakyThrows
    void exportBatch_whenNoEntryFound_shouldRetrieveDataAndSaveNewEntryToDatabase() {
        // given
        doReturn(fddbResponse).when(fddbAdapter).retrieveDataToTimeframe(timeframe);
        doReturn(fddbData).when(htmlParser).getDataFromResponse(fddbResponse);
        // when
        exportService.exportDataAndSaveToDb(timeframe);
        // then
        verify(fddbAdapter, times(1)).retrieveDataToTimeframe(timeframe);
        verify(htmlParser, times(1)).getDataFromResponse(fddbResponse);
        verify(persistenceService, times(1)).save(fddbData);
    }

    @Test
    @SneakyThrows
    void exportBatch_whenEntryForDatePresent_shouldRetrieveDataAndUpdateEntry() {
        // given
        doReturn(fddbResponse).when(fddbAdapter).retrieveDataToTimeframe(timeframe);
        doReturn(fddbData).when(htmlParser).getDataFromResponse(fddbResponse);
        doReturn(Optional.of(existingEntry)).when(persistenceService).find(Mockito.any());
        // when
        exportService.exportDataAndSaveToDb(timeframe);
        // then
        verify(fddbAdapter, times(1)).retrieveDataToTimeframe(timeframe);
        verify(htmlParser, times(1)).getDataFromResponse(fddbResponse);
        existingEntry.setCarbs(200);
        existingEntry.setId(123L);
        verify(persistenceService, times(1)).save(existingEntry);
    }

    private FddbData createFddbData(Date date) {
        return FddbData.builder()
                .kcal(2000)
                .carbs(200)
                .fat(100)
                .fiber(100)
                .protein(100)
                .sugar(100)
                .date(date)
                .build();
    }
}
