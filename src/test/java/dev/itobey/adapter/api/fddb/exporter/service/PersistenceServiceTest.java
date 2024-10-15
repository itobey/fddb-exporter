package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.InfluxDBService;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.MongoDBService;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistenceServiceTest {

    @InjectMocks
    private PersistenceService persistenceService;
    @Mock
    private FddbDataMapper fddbDataMapper;
    @Mock
    private MongoDBService mongoDBService;
    @Mock
    private InfluxDBService influxDBService;
    @Mock
    private FddbDataRepository fddbDataRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private FddbExporterProperties properties;

    private FddbData testFddbData;

    @BeforeEach
    void setUp() {
        testFddbData = new FddbData();
        testFddbData.setDate(LocalDate.now());
    }

    @Test
    void findAllEntries_shouldReturnAllEntries() {
        List<FddbData> expectedEntries = Arrays.asList(new FddbData(), new FddbData());
        when(mongoDBService.findAllEntries()).thenReturn(expectedEntries);

        List<FddbData> actualEntries = persistenceService.findAllEntries();

        assertEquals(expectedEntries, actualEntries);
        verify(mongoDBService).findAllEntries();
    }

    @Test
    void findByDate_shouldReturnOptionalOfFddbData() {
        LocalDate testDate = LocalDate.now();
        when(mongoDBService.findByDate(testDate)).thenReturn(Optional.of(testFddbData));

        Optional<FddbData> result = persistenceService.findByDate(testDate);

        assertTrue(result.isPresent());
        assertEquals(testFddbData, result.get());
        verify(mongoDBService).findByDate(testDate);
    }

    @Test
    void findByProduct_shouldReturnListOfProductWithDate() {
        String productName = "Test Product";
        List<ProductWithDate> expectedResults = Arrays.asList(new ProductWithDate(), new ProductWithDate());
        when(mongoDBService.findByProduct(productName)).thenReturn(expectedResults);

        List<ProductWithDate> actualResults = persistenceService.findByProduct(productName);

        assertEquals(expectedResults, actualResults);
        verify(mongoDBService).findByProduct(productName);
    }

    @Test
    void saveOrUpdate_shouldSkipExistingButIdenticalEntry() {
        FddbData existingData = new FddbData();
        existingData.setDate(LocalDate.now());

        when(mongoDBService.findByDate(testFddbData.getDate())).thenReturn(Optional.of(existingData));
        when(properties.getPersistence().getInfluxdb().isEnabled()).thenReturn(true);
        when(properties.getPersistence().getMongodb().isEnabled()).thenReturn(true);

        persistenceService.saveOrUpdate(testFddbData);

        verify(mongoDBService).findByDate(existingData.getDate());
        verify(fddbDataRepository, never()).save(any(FddbData.class));
        verify(influxDBService).saveToInfluxDB(testFddbData);
    }

    @Test
    void saveOrUpdate_shouldUpdateExistingEntryWithNewData() {
        FddbData existingData = new FddbData();
        existingData.setDate(LocalDate.now());
        existingData.setTotalCalories(100);

        testFddbData.setTotalCalories(200);

        when(mongoDBService.findByDate(existingData.getDate())).thenReturn(Optional.of(existingData));
        when(fddbDataRepository.save(existingData)).thenReturn(existingData);
        when(properties.getPersistence().getInfluxdb().isEnabled()).thenReturn(true);
        when(properties.getPersistence().getMongodb().isEnabled()).thenReturn(true);

        persistenceService.saveOrUpdate(testFddbData);

        verify(fddbDataMapper).updateFddbData(existingData, testFddbData);
        verify(fddbDataRepository).save(existingData);
        verify(influxDBService).saveToInfluxDB(testFddbData);
    }

    @Test
    void saveOrUpdate_shouldCreateNewEntry() {
        when(mongoDBService.findByDate(testFddbData.getDate())).thenReturn(Optional.empty());
        when(fddbDataRepository.save(testFddbData)).thenReturn(testFddbData);
        when(properties.getPersistence().getInfluxdb().isEnabled()).thenReturn(true);
        when(properties.getPersistence().getMongodb().isEnabled()).thenReturn(true);

        persistenceService.saveOrUpdate(testFddbData);

        verify(fddbDataRepository).save(testFddbData);
        verify(fddbDataMapper, never()).updateFddbData(any(), any());
        verify(influxDBService).saveToInfluxDB(testFddbData);
    }

    @Test
    void countAllEntries_shouldReturnCount() {
        long expectedCount = 10L;
        when(mongoDBService.countAllEntries()).thenReturn(expectedCount);

        long actualCount = persistenceService.countAllEntries();

        assertEquals(expectedCount, actualCount);
        verify(mongoDBService).countAllEntries();
    }

    @Test
    void countAllInfluxDbPoints_shouldReturnCount() {
        long expectedCount = 20L;
        when(influxDBService.getDataPointCount()).thenReturn(expectedCount);

        long actualCount = persistenceService.countAllInfluxDbPoints();

        assertEquals(expectedCount, actualCount);
        verify(influxDBService).getDataPointCount();
    }
}
