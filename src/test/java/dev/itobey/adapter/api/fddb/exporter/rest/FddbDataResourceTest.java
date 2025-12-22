package dev.itobey.adapter.api.fddb.exporter.rest;

import dev.itobey.adapter.api.fddb.exporter.dto.DateRangeDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ExportResultDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FddbDataResourceTest {

    @Mock
    private FddbDataService fddbDataService;

    @InjectMocks
    private FddbDataResource fddbDataResource;

    @Test
    void testFindAllEntries() {
        List<FddbDataDTO> mockData = Arrays.asList(new FddbDataDTO(), new FddbDataDTO());
        when(fddbDataService.findAllEntries()).thenReturn(mockData);

        ResponseEntity<List<FddbDataDTO>> response = fddbDataResource.findAllEntries();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindByDate_ValidDate() {
        String validDate = "2023-01-01";
        FddbDataDTO mockData = new FddbDataDTO();
        when(fddbDataService.findByDate(validDate)).thenReturn(Optional.of(mockData));

        ResponseEntity<?> response = fddbDataResource.findByDate(validDate);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Optional.of(mockData), response.getBody());
    }

    @Test
    void testFindByDate_InvalidDate() {
        String invalidDate = "2023-1-1";

        ResponseEntity<?> response = fddbDataResource.findByDate(invalidDate);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Date must be in the format YYYY-MM-DD", response.getBody());
    }

    @Test
    void testFindByDate_NotFound() {
        String validDate = "2023-01-01";
        when(fddbDataService.findByDate(validDate)).thenReturn(Optional.empty());

        ResponseEntity<?> response = fddbDataResource.findByDate(validDate);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testFindByProduct() {
        String productName = "TestProduct";
        List<ProductWithDateDTO> mockData = Arrays.asList(new ProductWithDateDTO(), new ProductWithDateDTO());
        when(fddbDataService.findByProduct(productName)).thenReturn(mockData);

        ResponseEntity<List<ProductWithDateDTO>> response = fddbDataResource.findByProduct(productName);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testExportForTimerange() {
        DateRangeDTO mockRequest = new DateRangeDTO();
        ExportResultDTO mockResult = new ExportResultDTO();
        when(fddbDataService.exportForTimerange(mockRequest)).thenReturn(mockResult);

        ResponseEntity<ExportResultDTO> response = fddbDataResource.exportForTimerange(mockRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResult, response.getBody());
    }

    @Test
    void testExportForDaysBack() {
        int days = 7;
        boolean includeToday = true;
        ExportResultDTO mockResult = new ExportResultDTO();
        when(fddbDataService.exportForDaysBack(days, includeToday)).thenReturn(mockResult);

        ResponseEntity<ExportResultDTO> response = fddbDataResource.exportForDaysBack(days, includeToday);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResult, response.getBody());
    }
}