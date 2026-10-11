package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.dto.ExtremeDirection;
import dev.itobey.adapter.api.fddb.exporter.dto.NutrientMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.StatsDTO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the statistics queries actually ask MongoDB for. The service-level behaviour is covered
 * by {@code StatsServiceTest}, which mocks this class - so what is worth asserting here is the query
 * shape that mocking hides: the date criteria, the sort direction, the excluded products array.
 */
@ExtendWith(MockitoExtension.class)
class StatsRepositoryTest {

    private static final String COLLECTION_NAME = "fddb";

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private StatsRepository statsRepository;

    @Test
    void countInRange_whenBothBoundsAreNull_shouldNotFilterOnDate() {
        when(mongoTemplate.count(any(Query.class), eq(COLLECTION_NAME))).thenReturn(357L);

        long result = statsRepository.countInRange(null, null);

        assertThat(result).isEqualTo(357L);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(query.capture(), eq(COLLECTION_NAME));
        assertThat(query.getValue().getQueryObject()).isEmpty();
    }

    @Test
    void countInRange_whenOnlyTheLowerBoundIsGiven_shouldFilterOnItAlone() {
        when(mongoTemplate.count(any(Query.class), eq(COLLECTION_NAME))).thenReturn(5L);

        statsRepository.countInRange(LocalDate.of(2024, 1, 1), null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(query.capture(), eq(COLLECTION_NAME));
        Document dateCriteria = (Document) query.getValue().getQueryObject().get("date");
        assertThat(dateCriteria.keySet()).containsExactly("$gte");
    }

    @Test
    void findFirstEntryDate_shouldSortAscendingAndLeaveOutTheProducts() {
        FddbData first = new FddbData();
        first.setDate(LocalDate.of(2024, 1, 1));
        when(mongoTemplate.findOne(any(Query.class), eq(FddbData.class), eq(COLLECTION_NAME))).thenReturn(first);

        LocalDate result = statsRepository.findFirstEntryDate();

        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 1));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(FddbData.class), eq(COLLECTION_NAME));
        assertThat(query.getValue().getSortObject().get("date")).isEqualTo(1);
        assertThat(query.getValue().getFieldsObject().get("products")).isEqualTo(0);
        assertThat(query.getValue().getLimit()).isEqualTo(1);
    }

    @Test
    void findLastEntryDate_whenTheDiaryIsEmpty_shouldReturnNull() {
        when(mongoTemplate.findOne(any(Query.class), eq(FddbData.class), eq(COLLECTION_NAME))).thenReturn(null);

        assertThat(statsRepository.findLastEntryDate()).isNull();
    }

    @Test
    void averages_whenTheRangeHoldsNothing_shouldReturnEmpty() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(StatsDTO.Averages.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));

        Optional<StatsDTO.Averages> result = statsRepository.averages(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(result).isEmpty();
    }

    @Test
    void averages_shouldReturnTheSingleAggregatedResult() {
        StatsDTO.Averages averages = StatsDTO.Averages.builder().avgTotalCalories(2000).build();
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(StatsDTO.Averages.class)))
                .thenReturn(new AggregationResults<>(List.of(averages), new Document()));

        assertThat(statsRepository.averages(null, null)).contains(averages);
    }

    @Test
    void findExtremeDays_whenLowestIsAsked_shouldSortAscending() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(StatsDTO.DayStats.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));

        statsRepository.findExtremeDays(NutrientMetric.CALORIES, ExtremeDirection.LOWEST, 3, null, null);

        ArgumentCaptor<Aggregation> aggregation = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate).aggregate(aggregation.capture(), eq(COLLECTION_NAME), eq(StatsDTO.DayStats.class));
        Document sortStage = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).stream()
                .filter(stage -> stage.containsKey("$sort"))
                .findFirst()
                .orElseThrow();
        assertThat(((Document) sortStage.get("$sort")).get("totalCalories")).isEqualTo(1);
    }

    /**
     * The six all-time extremes used to be six {@code sort + limit 1} aggregations. What makes one
     * enough is that {@code $max} over {@code {v, d}} compares the value first and carries the date
     * along, so this pins that shape rather than just the result.
     */
    @Test
    void findHighestDayPerMetric_shouldAskForAllSixMaximaInOneGroupStage() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(StatsRepository.HighestDays.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));

        statsRepository.findHighestDayPerMetric();

        ArgumentCaptor<Aggregation> aggregation = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate).aggregate(aggregation.capture(), eq(COLLECTION_NAME),
                eq(StatsRepository.HighestDays.class));
        List<Document> pipeline = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
        assertThat(pipeline).hasSize(2);

        Document group = (Document) pipeline.getFirst().get("$group");
        assertThat(group.keySet()).containsExactlyInAnyOrder(
                "_id", "totalCalories", "totalFat", "totalCarbs", "totalSugar", "totalProtein", "totalFibre");
        Document caloriesMax = (Document) ((Document) group.get("totalCalories")).get("$max");
        // value first, date second - reversing this would return the latest day, not the highest one
        assertThat(caloriesMax.keySet()).containsExactly("v", "d");
        assertThat(caloriesMax.get("v")).isEqualTo("$totalCalories");
        assertThat(caloriesMax.get("d")).isEqualTo("$date");
    }

    @Test
    void findHighestDayPerMetric_shouldMapEachProjectedPairToItsMetric() {
        StatsRepository.HighestDays highestDays = new StatsRepository.HighestDays();
        highestDays.setTotalCalories(StatsDTO.DayStats.builder().date(LocalDate.of(2024, 5, 1)).total(3200).build());
        highestDays.setTotalProtein(StatsDTO.DayStats.builder().date(LocalDate.of(2024, 6, 2)).total(180).build());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(StatsRepository.HighestDays.class)))
                .thenReturn(new AggregationResults<>(List.of(highestDays), new Document()));

        Map<NutrientMetric, StatsDTO.DayStats> result = statsRepository.findHighestDayPerMetric();

        // metrics the aggregation had nothing for are left out rather than mapped to a null-dated day
        assertThat(result).containsOnlyKeys(NutrientMetric.CALORIES, NutrientMetric.PROTEIN);
        assertThat(result.get(NutrientMetric.CALORIES).getDate()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(result.get(NutrientMetric.PROTEIN).getTotal()).isEqualTo(180.0);
    }

    @Test
    void findHighestDayPerMetric_whenTheDiaryIsEmpty_shouldReturnNoMetricsAtAll() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(StatsRepository.HighestDays.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));

        assertThat(statsRepository.findHighestDayPerMetric()).isEmpty();
    }

    @Test
    void countUniqueProducts_whenTheAggregationReturnsNothing_shouldBeZero() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));

        assertThat(statsRepository.countUniqueProducts()).isZero();
    }

    @Test
    void countTotalProducts_shouldReadItsOwnFieldName() {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(COLLECTION_NAME), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(new Document("totalCount", 120L)), new Document()));

        assertThat(statsRepository.countTotalProducts()).isEqualTo(120L);
    }

    @Test
    void findLoggedDates_shouldSkipDaysWithoutCalories() {
        FddbData logged = new FddbData();
        logged.setDate(LocalDate.of(2024, 1, 2));
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(COLLECTION_NAME))).thenReturn(List.of(logged));

        Set<LocalDate> result = statsRepository.findLoggedDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3));

        assertThat(result).containsExactly(LocalDate.of(2024, 1, 2));
        // a day with an entry but no calories is what an aborted export looks like, so it must not count
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(FddbData.class), eq(COLLECTION_NAME));
        Document calorieCriteria = (Document) query.getValue().getQueryObject().get("totalCalories");
        assertThat(calorieCriteria.keySet()).containsExactly("$gt");
    }

    @Test
    void findDailyTotals_shouldSortAscendingAndLeaveOutTheProducts() {
        when(mongoTemplate.find(any(Query.class), eq(FddbData.class), eq(COLLECTION_NAME))).thenReturn(List.of());

        statsRepository.findDailyTotals(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(FddbData.class), eq(COLLECTION_NAME));
        assertThat(query.getValue().getSortObject().get("date")).isEqualTo(1);
        assertThat(query.getValue().getFieldsObject().get("products")).isEqualTo(0);
        Document dateCriteria = (Document) query.getValue().getQueryObject().get("date");
        assertThat(dateCriteria.keySet()).containsExactlyInAnyOrder("$gte", "$lte");
    }
}
