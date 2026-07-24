package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductRanking;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TopProductDTO;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.MongoDBService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoDBServiceTest {

    private static final String COLLECTION_NAME = "fddb";

    @Mock
    private FddbDataRepository fddbDataRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoDBService mongoDBService;

    @Test
    void findByDateBetween_whenBothBoundsGiven_shouldUseTheDerivedRepositoryQuery() {
        // given
        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);
        List<FddbData> entries = List.of(new FddbData());
        when(fddbDataRepository.findInDateRange(fromDate, toDate)).thenReturn(entries);

        // when
        List<FddbData> result = mongoDBService.findByDateBetween(fromDate, toDate);

        // then
        assertThat(result).isEqualTo(entries);
        verify(fddbDataRepository).findInDateRange(fromDate, toDate);
    }

    @Test
    void findByProduct_shouldApplyWeekdayFilterAndLimit() {
        // given - a Monday, a Saturday and another Monday
        stubProductOccurrences(List.of(
                occurrence(LocalDate.of(2024, 1, 1), "Banana", 100),
                occurrence(LocalDate.of(2024, 1, 6), "Banana", 110),
                occurrence(LocalDate.of(2024, 1, 8), "Banana", 120)));

        // when
        List<ProductWithDate> result = mongoDBService.findByProduct(
                "banana", List.of(DayOfWeek.MONDAY), null, null, 1);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void findByProduct_whenLimitIsNull_shouldReturnEverything() {
        // given
        stubProductOccurrences(List.of(
                occurrence(LocalDate.of(2024, 1, 1), "Banana", 100),
                occurrence(LocalDate.of(2024, 1, 2), "Banana", 110)));

        // when
        List<ProductWithDate> result = mongoDBService.findByProduct("banana", null, null, null, null);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void getProductSummary_shouldAggregateOccurrences() {
        // given - 2024-01-01 is a Monday, 2024-01-06 a Saturday, 2024-01-08 a Monday
        stubProductOccurrences(List.of(
                occurrence(LocalDate.of(2024, 1, 1), "Haferflocken kernig", 300),
                occurrence(LocalDate.of(2024, 1, 6), "Haferflocken zart", 250),
                occurrence(LocalDate.of(2024, 1, 8), "Haferflocken kernig", 350)));

        // when
        ProductSummaryDTO result = mongoDBService.getProductSummary("hafer", null, null);

        // then
        assertThat(result.getSearchTerm()).isEqualTo("hafer");
        assertThat(result.getTimesEaten()).isEqualTo(3);
        assertThat(result.getFirstDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getLastDate()).isEqualTo(LocalDate.of(2024, 1, 8));
        assertThat(result.getTotalCalories()).isEqualTo(900.0);
        assertThat(result.getAverageCalories()).isEqualTo(300.0);
        assertThat(result.getMatchedProductNames())
                .containsExactly("Haferflocken kernig", "Haferflocken zart");
        assertThat(result.getWeekdayDistribution())
                .containsEntry(DayOfWeek.MONDAY, 2L)
                .containsEntry(DayOfWeek.SATURDAY, 1L);
    }

    @Test
    void getProductSummary_whenNothingMatches_shouldReturnEmptySummary() {
        // given
        stubProductOccurrences(List.of());

        // when
        ProductSummaryDTO result = mongoDBService.getProductSummary("nothing", null, null);

        // then
        assertThat(result.getTimesEaten()).isZero();
        assertThat(result.getFirstDate()).isNull();
        assertThat(result.getLastDate()).isNull();
        assertThat(result.getMatchedProductNames()).isEmpty();
        assertThat(result.getWeekdayDistribution()).isEmpty();
    }

    @Test
    void getTopProducts_shouldRoundTheAggregatedTotals() {
        // given
        TopProductDTO aggregated = TopProductDTO.builder()
                .name("Banana")
                .timesEaten(3)
                .totalCalories(300.06)
                .totalFat(1.24)
                .averageCalories(100.02)
                .build();
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(TopProductDTO.class)))
                .thenReturn(new AggregationResults<>(List.of(aggregated), new Document()));

        // when
        List<TopProductDTO> result = mongoDBService.getTopProducts(ProductRanking.FREQUENCY, null, null, 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTotalCalories()).isEqualTo(300.1);
        assertThat(result.getFirst().getTotalFat()).isEqualTo(1.2);
        assertThat(result.getFirst().getAverageCalories()).isEqualTo(100.0);
    }

    @Test
    void findDistinctProductNames_shouldUnwrapTheGroupedIds() {
        // given
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(
                        new Document("_id", "Haferflocken kernig"),
                        new Document("_id", "Haferflocken zart")), new Document()));

        // when
        List<String> result = mongoDBService.findDistinctProductNames("hafer", 50);

        // then
        assertThat(result).containsExactly("Haferflocken kernig", "Haferflocken zart");
    }

    private void stubProductOccurrences(List<ProductWithDate> occurrences) {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(ProductWithDate.class)))
                .thenReturn(new AggregationResults<>(occurrences, new Document()));
    }

    private ProductWithDate occurrence(LocalDate date, String name, double calories) {
        Product product = new Product();
        product.setName(name);
        product.setCalories(calories);

        ProductWithDate productWithDate = new ProductWithDate();
        productWithDate.setDate(date);
        productWithDate.setProduct(product);
        return productWithDate;
    }
}
