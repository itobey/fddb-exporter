package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistenceServiceTest {

    @Mock
    private FddbDataRepository fddbDataRepository;

    @Mock
    private FddbDataMapper fddbDataMapper;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private PersistenceService persistenceService;

    private FddbData testFddbData;

    @BeforeEach
    void setUp() {
        testFddbData = new FddbData();
        testFddbData.setDate(LocalDate.now());
    }

    @Test
    void findAllEntries_shouldReturnAllEntries() {
        List<FddbData> expectedEntries = Arrays.asList(new FddbData(), new FddbData());
        when(fddbDataRepository.findAll()).thenReturn(expectedEntries);

        List<FddbData> actualEntries = persistenceService.findAllEntries();

        assertEquals(expectedEntries, actualEntries);
        verify(fddbDataRepository).findAll();
    }

    @Test
    void findByDate_shouldReturnOptionalOfFddbData() {
        LocalDate testDate = LocalDate.now();
        when(fddbDataRepository.findFirstByDate(testDate)).thenReturn(Optional.of(testFddbData));

        Optional<FddbData> result = persistenceService.findByDate(testDate);

        assertTrue(result.isPresent());
        assertEquals(testFddbData, result.get());
        verify(fddbDataRepository).findFirstByDate(testDate);
    }

    @Test
    void findByProduct_shouldReturnListOfProductWithDate() {
        String productName = "Test Product";
        List<ProductWithDate> expectedResults = Arrays.asList(new ProductWithDate(), new ProductWithDate());

        AggregationResults<ProductWithDate> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(expectedResults);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("fddb"), eq(ProductWithDate.class)))
                .thenReturn(mockResults);

        List<ProductWithDate> actualResults = persistenceService.findByProduct(productName);

        assertEquals(expectedResults, actualResults);
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq("fddb"), eq(ProductWithDate.class));
    }

    @Test
    void saveOrUpdate_shouldSkipExistingButIdenticalEntry() {
        FddbData existingData = new FddbData();
        existingData.setDate(LocalDate.now());

        when(fddbDataRepository.findFirstByDate(testFddbData.getDate())).thenReturn(Optional.of(existingData));

        persistenceService.saveOrUpdate(testFddbData);

        verify(fddbDataRepository, times(1)).findFirstByDate(existingData.getDate());
    }

    @Test
    void saveOrUpdate_shouldUpdateExistingEntryWithNewData() {
        FddbData existingData = new FddbData();
        existingData.setDate(LocalDate.now());
        existingData.setTotalCalories(100);

        testFddbData.setTotalCalories(200);

        when(fddbDataRepository.findFirstByDate(existingData.getDate())).thenReturn(Optional.of(existingData));
        when(fddbDataRepository.save(testFddbData)).thenReturn(testFddbData);

        doAnswer(invocation -> {
            FddbData targetArg = invocation.getArgument(0);
            FddbData sourceArg = invocation.getArgument(1);
            targetArg.setTotalCalories(sourceArg.getTotalCalories());
            return null;
        }).when(fddbDataMapper).updateFddbData(any(FddbData.class), any(FddbData.class));


        persistenceService.saveOrUpdate(testFddbData);

        verify(fddbDataMapper).updateFddbData(existingData, testFddbData);
        verify(fddbDataRepository).save(testFddbData);
    }

    @Test
    void saveOrUpdate_shouldCreateNewEntry() {
        when(fddbDataRepository.findFirstByDate(testFddbData.getDate())).thenReturn(Optional.empty());
        when(fddbDataRepository.save(testFddbData)).thenReturn(testFddbData);

        persistenceService.saveOrUpdate(testFddbData);

        verify(fddbDataRepository).save(testFddbData);
        verify(fddbDataMapper, never()).updateFddbData(any(), any());
    }
}