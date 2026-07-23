package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.FddbDataDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductRanking;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductWithDateDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TopProductDTO;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit test for v2 Query API.
 */
@ExtendWith(MockitoExtension.class)
@Tag("v2")
class FddbDataQueryResourceV2Test {

    @Mock
    private FddbDataService fddbDataService;

    @InjectMocks
    private FddbDataQueryResourceV2 fddbDataQueryResourceV2;

    @Test
    void testFindAllEntries() {
        List<FddbDataDTO> mockData = Arrays.asList(new FddbDataDTO(), new FddbDataDTO());
        when(fddbDataService.findAllEntries()).thenReturn(mockData);

        ResponseEntity<List<FddbDataDTO>> response = fddbDataQueryResourceV2.findAllEntries();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindByDate_ValidDate() {
        String validDate = "2023-01-01";
        FddbDataDTO mockData = new FddbDataDTO();
        when(fddbDataService.findByDate(validDate)).thenReturn(Optional.of(mockData));

        ResponseEntity<?> response = fddbDataQueryResourceV2.findByDate(validDate);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Optional.of(mockData), response.getBody());
    }

    @Test
    void testFindByDate_InvalidDate() {
        String invalidDate = "2023-1-1";

        ResponseEntity<?> response = fddbDataQueryResourceV2.findByDate(invalidDate);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Date must be in the format YYYY-MM-DD", response.getBody());
    }

    @Test
    void testFindByDate_NotFound() {
        String validDate = "2023-01-01";
        when(fddbDataService.findByDate(validDate)).thenReturn(Optional.empty());

        ResponseEntity<?> response = fddbDataQueryResourceV2.findByDate(validDate);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testFindByProduct() {
        String productName = "TestProduct";
        List<ProductWithDateDTO> mockData = Arrays.asList(new ProductWithDateDTO(), new ProductWithDateDTO());
        when(fddbDataService.findByProduct(productName, null, null, null, null)).thenReturn(mockData);

        ResponseEntity<List<ProductWithDateDTO>> response =
                fddbDataQueryResourceV2.findByProduct(productName, null, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindByProductWithDays() {
        String productName = "TestProduct";
        List<DayOfWeek> days = Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        List<ProductWithDateDTO> mockData = Arrays.asList(new ProductWithDateDTO(), new ProductWithDateDTO());
        when(fddbDataService.findByProduct(productName, days, null, null, null)).thenReturn(mockData);

        ResponseEntity<List<ProductWithDateDTO>> response =
                fddbDataQueryResourceV2.findByProduct(productName, days, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindByProductWithDateRangeAndLimit() {
        String productName = "TestProduct";
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);
        List<ProductWithDateDTO> mockData = List.of(new ProductWithDateDTO());
        when(fddbDataService.findByProduct(productName, null, fromDate, toDate, 5)).thenReturn(mockData);

        ResponseEntity<List<ProductWithDateDTO>> response =
                fddbDataQueryResourceV2.findByProduct(productName, null, fromDate, toDate, 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindByDateRange() {
        LocalDate fromDate = LocalDate.of(2024, 12, 1);
        LocalDate toDate = LocalDate.of(2024, 12, 31);
        List<FddbDataDTO> mockData = Arrays.asList(new FddbDataDTO(), new FddbDataDTO());
        when(fddbDataService.findByDateRange(fromDate, toDate, false)).thenReturn(mockData);

        ResponseEntity<List<FddbDataDTO>> response = fddbDataQueryResourceV2.findByDateRange(fromDate, toDate, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindRecentDays() {
        List<FddbDataDTO> mockData = List.of(new FddbDataDTO());
        when(fddbDataService.findRecentDays(7, true)).thenReturn(mockData);

        ResponseEntity<List<FddbDataDTO>> response = fddbDataQueryResourceV2.findRecentDays(7, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindLatestEntry() {
        FddbDataDTO mockData = new FddbDataDTO();
        when(fddbDataService.findLatestEntry()).thenReturn(Optional.of(mockData));

        ResponseEntity<FddbDataDTO> response = fddbDataQueryResourceV2.findLatestEntry();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindLatestEntry_NoData() {
        when(fddbDataService.findLatestEntry()).thenReturn(Optional.empty());

        ResponseEntity<FddbDataDTO> response = fddbDataQueryResourceV2.findLatestEntry();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGetTopProducts() {
        List<TopProductDTO> mockData = List.of(TopProductDTO.builder().name("Banana").timesEaten(12).build());
        when(fddbDataService.getTopProducts(ProductRanking.FREQUENCY, null, null, 20)).thenReturn(mockData);

        ResponseEntity<List<TopProductDTO>> response =
                fddbDataQueryResourceV2.getTopProducts(ProductRanking.FREQUENCY, null, null, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testGetProductSummary() {
        ProductSummaryDTO mockData = ProductSummaryDTO.builder().searchTerm("Banana").timesEaten(3).build();
        when(fddbDataService.getProductSummary("Banana", null, null)).thenReturn(mockData);

        ResponseEntity<ProductSummaryDTO> response =
                fddbDataQueryResourceV2.getProductSummary("Banana", null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }

    @Test
    void testFindDistinctProductNames() {
        List<String> mockData = List.of("Haferflocken kernig", "Haferflocken zart");
        when(fddbDataService.findDistinctProductNames("hafer", 100)).thenReturn(mockData);

        ResponseEntity<List<String>> response = fddbDataQueryResourceV2.findDistinctProductNames("hafer", 100);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockData, response.getBody());
    }
}

