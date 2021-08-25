package com.itobey.adapter.api.fddb.exporter;

import com.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import com.itobey.adapter.api.fddb.exporter.domain.FddbBatchExport;
import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import com.itobey.adapter.api.fddb.exporter.exception.ManualExporterException;
import com.itobey.adapter.api.fddb.exporter.repository.FddbRepository;
import com.itobey.adapter.api.fddb.exporter.service.HtmlParser;
import com.itobey.adapter.api.fddb.exporter.service.ManualExporterService;
import com.itobey.adapter.api.fddb.exporter.service.TimeframeCalculator;
import org.apache.http.auth.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class ManualExporterServiceTest {

    @InjectMocks
    ManualExporterService manualExporterService;

    @Mock
    TimeframeCalculator timeframeCalculator;
    @Mock
    FddbAdapter fddbAdapter;
    @Mock
    HtmlParser htmlParser;
    @Mock
    FddbRepository fddbRepository;

    Timeframe timeframe;
    String fddbResponse;
    FddbData fddbData;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        timeframe = Timeframe.builder().from(123).to(456).build();
        fddbResponse = "<html>something</html>";
        fddbData = FddbData.builder().carbs(100).build();
    }

    @Test
    public void exportBatch_whenPayloadValid_shouldRetrieveDataAndSaveToDatabase() throws ManualExporterException, AuthenticationException {
        // given
        doReturn(timeframe).when(timeframeCalculator).calculateTimeframeFor(Mockito.any());
        doReturn(fddbResponse).when(fddbAdapter).retrieveDataToTimeframe(timeframe);
        doReturn(fddbData).when(htmlParser).getDataFromResponse(fddbResponse);
        FddbBatchExport fddbBatchExport = FddbBatchExport.builder()
                .fromDate("2021-08-15T10:10:10")
                .toDate("2021-08-15T10:10:10")
                .build();
        // when
        manualExporterService.exportBatch(fddbBatchExport);
        // then
        verify(timeframeCalculator, times(1)).calculateTimeframeFor(Mockito.any());
        verify(fddbAdapter, times(1)).retrieveDataToTimeframe(timeframe);
        verify(htmlParser, times(1)).getDataFromResponse(fddbResponse);
        verify(fddbRepository, times(1)).save(fddbData);

    }
}
