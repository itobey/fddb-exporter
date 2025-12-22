package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private StatsService statsService;

    @Test
    void getStats_shouldGatherAllData() {
        // given
        when(mongoTemplate.count(any(Query.class), eq(StatsService.COLLECTION_NAME))).thenReturn(100L);

        FddbData mockData = new FddbData();
        mockData.setDate(LocalDate.of(2023, 1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(FddbData.class), eq(StatsService.COLLECTION_NAME))).thenReturn(mockData);

        StatsDTO.Averages mockAverages = StatsDTO.Averages.builder()
                .avgTotalCalories(2000)
                .avgTotalFat(70)
                .avgTotalCarbs(250)
                .avgTotalSugar(50)
                .avgTotalProtein(100)
                .avgTotalFibre(30)
                .build();

        Document rawResults = new Document();
        AggregationResults<StatsDTO.Averages> mockResults = new AggregationResults<>(Collections.singletonList(mockAverages), rawResults);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.Averages.class)))
                .thenReturn(mockResults);

        StatsDTO.DayStats mockDayStats = StatsDTO.DayStats.builder()
                .date(LocalDate.of(2023, 5, 1))
                .total(3000)
                .build();

        AggregationResults<StatsDTO.DayStats> mockDayStatsResults = new AggregationResults<>(Collections.singletonList(mockDayStats), rawResults);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(StatsDTO.DayStats.class)))
                .thenReturn(mockDayStatsResults);

        AggregationResults<Document> mockUniqueProductsResults = new AggregationResults<>(
                Collections.singletonList(new Document("uniqueCount", 42L)), rawResults);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(StatsService.COLLECTION_NAME), eq(Document.class)))
                .thenReturn(mockUniqueProductsResults);

        // when
        StatsDTO result = statsService.getStats();

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAmountEntries()).isEqualTo(100L);
        assertThat(result.getFirstEntryDate()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(result.getEntryPercentage()).isGreaterThan(0);
        assertThat(result.getUniqueProducts()).isEqualTo(42L);
        assertThat(result.getAverageTotals()).isEqualTo(mockAverages);
        assertThat(result.getHighestCaloriesDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestFatDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestCarbsDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestProteinDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestFibreDay()).isEqualTo(mockDayStats);
        assertThat(result.getHighestSugarDay()).isEqualTo(mockDayStats);
        assertThat(result.getMostRecentMissingDay()).isNotNull();
    }

}
