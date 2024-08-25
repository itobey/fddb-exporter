package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Timeframe;
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
    FddbParserService fddbParserService;
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
    }

    @Test
    @SneakyThrows
    void exportBatch_whenNoEntryFound_shouldRetrieveDataAndSaveNewEntryToDatabase() {
        //TODO
    }

    @Test
    @SneakyThrows
    void exportBatch_whenEntryForDatePresent_shouldRetrieveDataAndUpdateEntry() {
        //TODO
    }

}
