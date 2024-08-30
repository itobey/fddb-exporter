package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @InjectMocks
    private ExportService exportService;

    @Mock
    private FddbAdapter fddbAdapter;

    @Mock
    private FddbParserService fddbParserService;

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private FddbDataMapper fddbDataMapper;

    private Timeframe timeframe;
    private String fddbResponse;
    private FddbData fddbData;
    private LocalDate testDate;

    @BeforeEach
    public void setUp() {
        long epochSecond = 1622505600; // 2021-06-01 00:00:00 UTC
        timeframe = new Timeframe(epochSecond, epochSecond + 86400);
        fddbResponse = "<html>mock FDDB response</html>";
//        testDate = new Date(Instant.ofEpochSecond(epochSecond).toEpochMilli());
        testDate = LocalDate.ofEpochDay(epochSecond / 86400);
        fddbData = new FddbData();
        fddbData.setDate(testDate);
    }

    @Test
    @SneakyThrows
    void exportDataAndSaveToDb_whenNoEntryFound_shouldRetrieveDataAndSaveNewEntryToDatabase() {
        // given
        when(fddbAdapter.retrieveDataToTimeframe(timeframe)).thenReturn(fddbResponse);
        when(fddbParserService.parseDiary(fddbResponse)).thenReturn(fddbData);
        when(persistenceService.findByDate(testDate)).thenReturn(Optional.empty());
        when(persistenceService.save(fddbData)).thenReturn(fddbData);

        // when
        FddbData result = exportService.exportDataAndSaveToDb(timeframe);

        // then
        assertEquals(fddbData, result);
        verify(fddbAdapter).retrieveDataToTimeframe(timeframe);
        verify(fddbParserService).parseDiary(fddbResponse);
        verify(persistenceService).findByDate(testDate);
        verify(persistenceService).save(fddbData);
        verifyNoInteractions(fddbDataMapper);
    }

    @Test
    @SneakyThrows
    void exportDataAndSaveToDb_whenEntryForDatePresent_shouldRetrieveDataAndUpdateEntry() {
        // given
        FddbData existingEntry = new FddbData();
        existingEntry.setDate(testDate);

        when(fddbAdapter.retrieveDataToTimeframe(timeframe)).thenReturn(fddbResponse);
        when(fddbParserService.parseDiary(fddbResponse)).thenReturn(fddbData);
        when(persistenceService.findByDate(testDate)).thenReturn(Optional.of(existingEntry));
        when(persistenceService.save(existingEntry)).thenReturn(existingEntry);

        // when
        FddbData result = exportService.exportDataAndSaveToDb(timeframe);

        // then
        assertEquals(existingEntry, result);
        verify(fddbAdapter).retrieveDataToTimeframe(timeframe);
        verify(fddbParserService).parseDiary(fddbResponse);
        verify(persistenceService).findByDate(testDate);
        verify(fddbDataMapper).updateFddbData(existingEntry, fddbData);
        verify(persistenceService).save(existingEntry);
    }

    @Test
    @SneakyThrows
    void exportDataAndSaveToDb_whenAuthenticationFails_shouldThrowAuthenticationException() {
        // given
        when(fddbParserService.parseDiary(any())).thenThrow(new AuthenticationException());

        // when & then
        assertThrows(AuthenticationException.class, () -> exportService.exportDataAndSaveToDb(timeframe));
        verify(fddbAdapter).retrieveDataToTimeframe(timeframe);
        verifyNoInteractions(persistenceService, fddbDataMapper);
    }

    @Test
    @SneakyThrows
    void exportDataAndSaveToDb_whenParsingFails_shouldThrowParseException() {
        // given
        when(fddbAdapter.retrieveDataToTimeframe(timeframe)).thenReturn(fddbResponse);
        when(fddbParserService.parseDiary(fddbResponse)).thenThrow(new ParseException("Parsing failed"));

        // when & then
        assertThrows(ParseException.class, () -> exportService.exportDataAndSaveToDb(timeframe));
        verify(fddbAdapter).retrieveDataToTimeframe(timeframe);
        verify(fddbParserService).parseDiary(fddbResponse);
        verifyNoInteractions(persistenceService, fddbDataMapper);
    }
}