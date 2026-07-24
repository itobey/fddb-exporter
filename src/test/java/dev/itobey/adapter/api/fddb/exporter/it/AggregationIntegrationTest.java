package dev.itobey.adapter.api.fddb.exporter.it;

import dev.itobey.adapter.api.fddb.exporter.config.TestConfig;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import dev.itobey.adapter.api.fddb.exporter.dto.*;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import dev.itobey.adapter.api.fddb.exporter.service.FddbDataService;
import dev.itobey.adapter.api.fddb.exporter.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Exercises the range queries and product/stats aggregations against a real MongoDB, since the
 * unit tests mock {@code MongoTemplate} and therefore cannot verify the aggregation pipelines
 * themselves.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestConfig.class)
class AggregationIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.9");

    @Container
    static InfluxDBContainer<?> influxDBContainer =
            new InfluxDBContainer<>(DockerImageName.parse("influxdb:2.0.7")).withAdminToken("token");

    @Autowired
    private FddbDataService fddbDataService;
    @Autowired
    private StatsService statsService;
    @Autowired
    private FddbDataRepository fddbDataRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("fddb-exporter.influxdb.url", () -> "http://localhost:" + influxDBContainer.getMappedPort(8086));
    }

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(FddbData.class);
        // 2024-01-01 Mon, 2024-01-02 Tue, 2024-01-06 Sat, 2024-01-08 Mon; 2024-01-03..05 and 07 are gaps
        fddbDataRepository.saveAll(List.of(
                day(LocalDate.of(2024, 1, 1), 2000, 100, 200, 50,
                        product("Haferflocken kernig", 300, 5, 50, 10),
                        product("Banane", 90, 0.3, 21, 1)),
                day(LocalDate.of(2024, 1, 2), 2500, 110, 250, 60,
                        product("Haferflocken kernig", 350, 6, 58, 12)),
                day(LocalDate.of(2024, 1, 6), 3500, 150, 350, 70,
                        product("Banane", 90, 0.3, 21, 1),
                        product("Pizza Salami", 1200, 60, 120, 45)),
                day(LocalDate.of(2024, 1, 8), 1500, 60, 150, 40,
                        product("Banane", 90, 0.3, 21, 1))));
    }

    @Test
    void findByDateRange_shouldReturnOnlyTheRangeOrderedByDate() {
        List<FddbDataDTO> result = fddbDataService.findByDateRange(
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 6), false);

        assertThat(result).extracting(FddbDataDTO::getDate)
                .containsExactly(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 6));
        assertThat(result).allSatisfy(entry -> assertThat(entry.getProducts()).isNull());
    }

    @Test
    void findByDateRange_whenProductsRequested_shouldIncludeThem() {
        List<FddbDataDTO> result = fddbDataService.findByDateRange(
                LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 6), true);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getProducts()).hasSize(2);
    }

    @Test
    void findByProduct_shouldRespectDateRangeAndLimit() {
        List<ProductWithDateDTO> all = fddbDataService.findByProduct("banane", null, null, null, null);
        assertThat(all).hasSize(3);

        List<ProductWithDateDTO> ranged = fddbDataService.findByProduct(
                "banane", null, LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 8), null);
        assertThat(ranged).extracting(ProductWithDateDTO::getDate)
                .containsExactly(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 6));

        List<ProductWithDateDTO> limited = fddbDataService.findByProduct("banane", null, null, null, 2);
        assertThat(limited).hasSize(2);
    }

    @Test
    void getTopProducts_shouldRankByFrequencyAndByCalories() {
        List<TopProductDTO> byFrequency = fddbDataService.getTopProducts(ProductRanking.FREQUENCY, null, null, 10);
        assertThat(byFrequency.getFirst().getName()).isEqualTo("Banane");
        assertThat(byFrequency.getFirst().getTimesEaten()).isEqualTo(3);
        assertThat(byFrequency.getFirst().getTotalCalories()).isEqualTo(270.0);
        assertThat(byFrequency.getFirst().getAverageCalories()).isEqualTo(90.0);

        List<TopProductDTO> byCalories = fddbDataService.getTopProducts(ProductRanking.CALORIES, null, null, 10);
        assertThat(byCalories.getFirst().getName()).isEqualTo("Pizza Salami");
        assertThat(byCalories.getFirst().getTotalCalories()).isEqualTo(1200.0);
    }

    @Test
    void getTopProducts_shouldHonourTheDateRange() {
        List<TopProductDTO> result = fddbDataService.getTopProducts(
                ProductRanking.FREQUENCY, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), 10);

        assertThat(result).extracting(TopProductDTO::getName)
                .containsExactlyInAnyOrder("Haferflocken kernig", "Banane");
        assertThat(result).filteredOn(product -> product.getName().equals("Haferflocken kernig"))
                .first()
                .satisfies(product -> assertThat(product.getTimesEaten()).isEqualTo(2));
    }

    @Test
    void getProductSummary_shouldAggregateOverTheUnwoundProducts() {
        ProductSummaryDTO result = fddbDataService.getProductSummary("hafer", null, null);

        assertThat(result.getTimesEaten()).isEqualTo(2);
        assertThat(result.getFirstDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getLastDate()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(result.getTotalCalories()).isEqualTo(650.0);
        assertThat(result.getAverageCalories()).isEqualTo(325.0);
        assertThat(result.getMatchedProductNames()).containsExactly("Haferflocken kernig");
        assertThat(result.getWeekdayDistribution())
                .containsEntry(DayOfWeek.MONDAY, 1L)
                .containsEntry(DayOfWeek.TUESDAY, 1L);
    }

    @Test
    void findDistinctProductNames_shouldReturnSortedUniqueNames() {
        assertThat(fddbDataService.findDistinctProductNames(null, 100))
                .containsExactly("Banane", "Haferflocken kernig", "Pizza Salami");
        assertThat(fddbDataService.findDistinctProductNames("an", 100))
                .containsExactly("Banane");
        assertThat(fddbDataService.findDistinctProductNames(null, 2))
                .hasSize(2);
    }

    @Test
    void getExtremeDays_shouldRankWithinTheRange() {
        List<StatsDTO.DayStats> highest = statsService.getExtremeDays(
                NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 2, null, null);
        assertThat(highest).extracting(StatsDTO.DayStats::getDate)
                .containsExactly(LocalDate.of(2024, 1, 6), LocalDate.of(2024, 1, 2));
        assertThat(highest.getFirst().getTotal()).isEqualTo(3500.0);

        List<StatsDTO.DayStats> lowest = statsService.getExtremeDays(
                NutrientMetric.CALORIES, ExtremeDirection.LOWEST, 1, null, null);
        assertThat(lowest.getFirst().getDate()).isEqualTo(LocalDate.of(2024, 1, 8));

        List<StatsDTO.DayStats> ranged = statsService.getExtremeDays(
                NutrientMetric.CALORIES, ExtremeDirection.HIGHEST, 5,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2));
        assertThat(ranged).extracting(StatsDTO.DayStats::getDate)
                .containsExactly(LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 1));
    }

    @Test
    void getTrend_shouldBucketByIsoWeek() {
        List<TrendPointDTO> result = fddbDataService.getTrend(NutrientMetric.CALORIES,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 14), TrendGranularity.WEEK);

        assertThat(result).extracting(TrendPointDTO::getBucket).containsExactly("2024-W01", "2024-W02");
        assertThat(result.getFirst().getDayCount()).isEqualTo(3);
        assertThat(result.getFirst().getAverage()).isEqualTo(2666.7);
        assertThat(result.getLast().getDayCount()).isEqualTo(1);
        assertThat(result.getLast().getAverage()).isEqualTo(1500.0);
    }

    @Test
    void getWeekdayBreakdown_shouldGroupByDayOfWeek() {
        List<WeekdayStatsDTO> result = fddbDataService.getWeekdayBreakdown(null, null);

        assertThat(result).extracting(WeekdayStatsDTO::getDayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.SATURDAY);
        assertThat(result.getFirst().getDayCount()).isEqualTo(2);
        assertThat(result.getFirst().getAverages().getAvgTotalCalories()).isEqualTo(1750.0);
    }

    @Test
    void getMacroSplit_shouldBeKcalWeighted() {
        // 2024-01-01 only: 100g fat = 900 kcal, 200g carbs = 800 kcal, 50g protein = 200 kcal
        MacroSplitDTO result = fddbDataService.getMacroSplit(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1));

        assertThat(result.getMacroCalories()).isEqualTo(1900.0);
        assertThat(result.getFatPercentage()).isEqualTo(47.4);
        assertThat(result.getCarbsPercentage()).isEqualTo(42.1);
        assertThat(result.getProteinPercentage()).isEqualTo(10.5);
    }

    @Test
    void getMissingDays_shouldListTheGaps() {
        List<LocalDate> result = fddbDataService.getMissingDays(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 8));

        assertThat(result).containsExactly(
                LocalDate.of(2024, 1, 3),
                LocalDate.of(2024, 1, 4),
                LocalDate.of(2024, 1, 5),
                LocalDate.of(2024, 1, 7));
    }

    @Test
    void getStats_shouldReportCoverageAcrossTheWholeHistory() {
        StatsDTO result = fddbDataService.getStats();

        assertThat(result.getFirstEntryDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(result.getLastEntryDate()).isEqualTo(LocalDate.of(2024, 1, 8));
        // the four gaps inside the fixture plus every day since the last entry
        assertThat(result.getMissingDaysCount()).isGreaterThan(4L);
        assertThat(result.getMostRecentMissingDay()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(result.getCurrentStreak()).isZero();
        // 2024-01-01 and -02 are the only two consecutive logged days
        assertThat(result.getLongestStreak()).isEqualTo(2);
    }

    private FddbData day(LocalDate date, double calories, double fat, double carbs, double protein,
                         Product... products) {
        FddbData data = new FddbData();
        data.setDate(date);
        data.setTotalCalories(calories);
        data.setTotalFat(fat);
        data.setTotalCarbs(carbs);
        data.setTotalProtein(protein);
        data.setTotalSugar(0);
        data.setTotalFibre(0);
        data.setProducts(List.of(products));
        return data;
    }

    private Product product(String name, double calories, double fat, double carbs, double protein) {
        Product product = new Product();
        product.setName(name);
        product.setAmount("100 g");
        product.setCalories(calories);
        product.setFat(fat);
        product.setCarbs(carbs);
        product.setProtein(protein);
        return product;
    }
}
