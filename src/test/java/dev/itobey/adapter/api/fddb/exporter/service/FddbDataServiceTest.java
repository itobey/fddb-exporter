package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import dev.itobey.adapter.api.fddb.exporter.mapper.FddbDataMapper;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.PersistenceService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbDataServiceTest {

    @InjectMocks
    private FddbDataService fddbDataService;

    @Mock
    private TimeframeCalculator timeframeCalculator;

    @Mock
    private ExportService exportService;

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private FddbDataMapper fddbDataMapper;

    private FddbData mockFddbData;
    private FddbDataDTO mockFddbDataDTO;
    private ProductWithDate mockProductWithDate;
    private ProductWithDateDTO mockProductWithDateDTO;

    @BeforeEach
    void setUp() {
        mockFddbData = mock(FddbData.class);
        mockFddbDataDTO = mock(FddbDataDTO.class);
        mockProductWithDate = mock(ProductWithDate.class);
        mockProductWithDateDTO = mock(ProductWithDateDTO.class);
    }

    @Test
    void findAllEntries_shouldQueryAllEntriesFromDatabase() {
        // given
        List<FddbData> fddbDataList = List.of(mockFddbData);
        when(persistenceService.findAllEntries()).thenReturn(fddbDataList);
        List<FddbDataDTO> fddbDataDTOList = List.of(mockFddbDataDTO);
        when(fddbDataMapper.toFddbDataDTO(fddbDataList)).thenReturn(fddbDataDTOList);

        // when
        List<FddbDataDTO> result = fddbDataService.findAllEntries();

        // then
        assertEquals(fddbDataDTOList, result);
        verify(persistenceService).findAllEntries();
        verify(fddbDataMapper).toFddbDataDTO(fddbDataList);
    }

    @Test
    void findByProduct_shouldQueryProductInDatabase() {
        // given
        String productName = "Product1";
        List<ProductWithDate> productWithDateList = List.of(mockProductWithDate);
        when(persistenceService.findByProduct(productName)).thenReturn(productWithDateList);
        List<ProductWithDateDTO> productWithDateDTOList = List.of(mockProductWithDateDTO);
        when(fddbDataMapper.toProductWithDateDto(productWithDateList)).thenReturn(productWithDateDTOList);

        // when
        List<ProductWithDateDTO> result = fddbDataService.findByProduct(productName);

        // then
        assertEquals(productWithDateDTOList, result);
        verify(persistenceService).findByProduct(productName);
        verify(fddbDataMapper).toProductWithDateDto(productWithDateList);
    }

    @Test
    void findByDate_shouldQueryDateInDatabase() {
        // given
        String dateString = "2023-09-01";
        LocalDate date = LocalDate.parse(dateString);
        when(persistenceService.findByDate(date)).thenReturn(Optional.of(mockFddbData));
        when(fddbDataMapper.toFddbDataDTO(mockFddbData)).thenReturn(mockFddbDataDTO);

        // when
        Optional<FddbDataDTO> result = fddbDataService.findByDate(dateString);

        // then
        assertTrue(result.isPresent());
        assertEquals(mockFddbDataDTO, result.get());
        verify(persistenceService).findByDate(date);
        verify(fddbDataMapper).toFddbDataDTO(mockFddbData);
    }

    @Test
    @SneakyThrows
    void exportForTimerange_whenPayloadValid_shouldReturnSuccessfulDays() {
        // given
        ExportRequestDTO exportRequestDTO = new ExportRequestDTO("2021-08-15", "2021-08-16");
        TimeframeDTO timeframeDTO = new TimeframeDTO(1628985600, 1629072000);

        when(timeframeCalculator.calculateTimeframeFor(any(LocalDate.class))).thenReturn(timeframeDTO);
        when(exportService.exportData(timeframeDTO)).thenReturn(mockFddbData);

        // when
        ExportResultDTO result = fddbDataService.exportForTimerange(exportRequestDTO);

        // then
        assertEquals(2, result.getSuccessfulDays().size());
        assertTrue(result.getSuccessfulDays().contains("2021-08-15"));
        assertTrue(result.getSuccessfulDays().contains("2021-08-16"));
        assertTrue(result.getUnsuccessfulDays().isEmpty());
        verify(timeframeCalculator, times(2)).calculateTimeframeFor(any(LocalDate.class));
        verify(exportService, times(2)).exportData(timeframeDTO);
        verify(persistenceService, times(2)).saveOrUpdate(mockFddbData);
    }

    @Test
    @SneakyThrows
    void exportForTimerange_whenExportFails_shouldReturnUnsuccessfulDays() {
        // given
        ExportRequestDTO exportRequestDTO = new ExportRequestDTO("2021-08-15", "2021-08-16");
        TimeframeDTO timeframeDTO = new TimeframeDTO(1628985600, 1629072000);

        when(timeframeCalculator.calculateTimeframeFor(any(LocalDate.class))).thenReturn(timeframeDTO);
        when(exportService.exportData(timeframeDTO))
                .thenReturn(mockFddbData)
                .thenThrow(new ParseException("Failed to parse"));

        // when
        ExportResultDTO result = fddbDataService.exportForTimerange(exportRequestDTO);

        // then
        assertEquals(1, result.getSuccessfulDays().size());
        assertEquals(1, result.getUnsuccessfulDays().size());
        assertTrue(result.getSuccessfulDays().contains("2021-08-15"));
        assertTrue(result.getUnsuccessfulDays().contains("2021-08-16"));
        verify(timeframeCalculator, times(2)).calculateTimeframeFor(any(LocalDate.class));
        verify(exportService, times(2)).exportData(timeframeDTO);
        verify(persistenceService, times(1)).saveOrUpdate(mockFddbData);
    }

    @Test
    void exportForTimerange_whenFromIsAfterTo_shouldThrowException() {
        // given
        ExportRequestDTO exportRequestDTO = new ExportRequestDTO("2023-01-20", "2023-01-15");

        // when & then
        DateTimeException exception = assertThrows(DateTimeException.class,
                () -> fddbDataService.exportForTimerange(exportRequestDTO));
        assertEquals("The 'from' date cannot be after the 'to' date", exception.getMessage());
        verifyNoInteractions(persistenceService);
    }

    @ParameterizedTest
    @CsvSource({
            "2, true",
            "2, false"
    })
    @SneakyThrows
    void exportForDaysBack_shouldGenerateTimeframeAccordingly(int days, boolean includeToday) {
        // given
        LocalDate today = LocalDate.now();
        LocalDate endDate = includeToday ? today : today.minusDays(1);
        LocalDate startDate = endDate.minusDays(days - 1);

        when(timeframeCalculator.calculateTimeframeFor(any(LocalDate.class))).thenReturn(mock(TimeframeDTO.class));
        when(exportService.exportData(any(TimeframeDTO.class))).thenReturn(mockFddbData);

        // when
        ExportResultDTO result = fddbDataService.exportForDaysBack(days, includeToday);

        // then
        assertEquals(days, result.getSuccessfulDays().size());
        assertTrue(result.getUnsuccessfulDays().isEmpty());
        verify(timeframeCalculator, times(days)).calculateTimeframeFor(any(LocalDate.class));
        verify(exportService, times(days)).exportData(any(TimeframeDTO.class));

        for (int i = 0; i < days; i++) {
            verify(timeframeCalculator).calculateTimeframeFor(startDate.plusDays(i));
        }
        verify(persistenceService, times(2)).saveOrUpdate(mockFddbData);
    }

    @Test
    void exportForDaysBack_whenDaysOutOfRange_shouldThrowException() {
        assertThrows(DateTimeException.class, () -> fddbDataService.exportForDaysBack(0, true));
        assertThrows(DateTimeException.class, () -> fddbDataService.exportForDaysBack(366, true));
        verifyNoInteractions(persistenceService);
    }
}