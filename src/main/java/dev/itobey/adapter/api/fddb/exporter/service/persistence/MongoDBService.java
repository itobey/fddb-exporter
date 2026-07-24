package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductRanking;
import dev.itobey.adapter.api.fddb.exporter.dto.ProductSummaryDTO;
import dev.itobey.adapter.api.fddb.exporter.dto.TopProductDTO;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Provides MongoDB-related services for managing {@link FddbData} objects.
 * <p>
 * This does not use Lomboks constructor, because the required=false is not supported by Lombok.
 */
@Service
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class MongoDBService {

    private static final String COLLECTION_NAME = "fddb";

    @Autowired(required = false)
    private FddbDataRepository fddbDataRepository;
    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    public long countAllEntries() {
        return fddbDataRepository.count();
    }

    /**
     * Retrieves all {@link FddbData} objects stored in the database.
     *
     * @return a list of all {@link FddbData} objects
     */
    public List<FddbData> findAllEntries() {
        return fddbDataRepository.findAll();
    }

    /**
     * Retrieves an entry to a given date.
     *
     * @param date the date to find an entry to
     * @return an Optional of {@link FddbData}
     */
    public Optional<FddbData> findByDate(LocalDate date) {
        return fddbDataRepository.findFirstByDate(date);
    }

    /**
     * Retrieves all entries between two dates, both bounds inclusive, oldest first.
     * Either bound may be null to leave that side of the range open.
     *
     * @param fromDate the first date to include, or null for no lower bound
     * @param toDate   the last date to include, or null for no upper bound
     * @return the matching entries ordered by date ascending
     */
    public List<FddbData> findByDateBetween(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            return fddbDataRepository.findInDateRange(fromDate, toDate);
        }

        Query query = new Query();
        Criteria criteria = buildDateCriteria(fromDate, toDate);
        if (criteria != null) {
            query.addCriteria(criteria);
        }
        query.with(Sort.by(Sort.Direction.ASC, "date"));
        return mongoTemplate.find(query, FddbData.class, COLLECTION_NAME);
    }

    /**
     * Searches for a product name and returns the date with the product details.
     * Unfortunately an aggregation annotation query did not work, maybe because I'm stuck with Mongo 4.4.
     *
     * @param name the name of the product
     * @return a list of matches
     */
    public List<ProductWithDate> findByProduct(String name) {
        return findByProduct(name, null);
    }

    /**
     * Searches for a product name and returns the date with the product details,
     * optionally filtered by days of the week.
     *
     * @param name       the name of the product
     * @param daysOfWeek list of days to filter by (e.g., MONDAY, WEDNESDAY). If null or empty, no day filtering is applied.
     * @return a list of matches
     */
    public List<ProductWithDate> findByProduct(String name, List<DayOfWeek> daysOfWeek) {
        return findByProduct(name, daysOfWeek, null, null, null);
    }

    /**
     * Searches for a product name and returns the date with the product details, optionally narrowed
     * down by days of the week, a date range and a maximum number of results.
     *
     * @param name       the name of the product
     * @param daysOfWeek list of days to filter by (e.g., MONDAY, WEDNESDAY). If null or empty, no day filtering is applied.
     * @param fromDate   the earliest date to include, or null for no lower bound
     * @param toDate     the latest date to include, or null for no upper bound
     * @param limit      the maximum number of results, or null/non-positive for no limit
     * @return a list of matches, newest first
     */
    public List<ProductWithDate> findByProduct(String name, List<DayOfWeek> daysOfWeek,
                                               LocalDate fromDate, LocalDate toDate, Integer limit) {
        List<ProductWithDate> results = findProductOccurrences(name, fromDate, toDate);

        if (daysOfWeek != null && !daysOfWeek.isEmpty()) {
            results = results.stream()
                    .filter(result -> result.getDate() != null && daysOfWeek.contains(result.getDate().getDayOfWeek()))
                    .toList();
        }

        if (limit != null && limit > 0 && results.size() > limit) {
            return List.copyOf(results.subList(0, limit));
        }

        return results;
    }

    /**
     * Aggregates every occurrence of the products matching a search term into a single summary:
     * how often they were logged, first and last date, the totals they contributed and how the
     * occurrences distribute over the days of the week.
     *
     * @param name     the product name to search for (case-insensitive substring)
     * @param fromDate the earliest date to include, or null for no lower bound
     * @param toDate   the latest date to include, or null for no upper bound
     * @return the summary, with zeroed counters and null dates if nothing matched
     */
    public ProductSummaryDTO getProductSummary(String name, LocalDate fromDate, LocalDate toDate) {
        List<ProductWithDate> occurrences = findProductOccurrences(name, fromDate, toDate);

        if (occurrences.isEmpty()) {
            return ProductSummaryDTO.builder()
                    .searchTerm(name)
                    .timesEaten(0)
                    .matchedProductNames(List.of())
                    .weekdayDistribution(new EnumMap<>(DayOfWeek.class))
                    .build();
        }

        ProductOccurrenceAggregate aggregate = aggregateOccurrences(occurrences);

        return ProductSummaryDTO.builder()
                .searchTerm(name)
                .timesEaten(occurrences.size())
                .matchedProductNames(aggregate.sortedNames())
                .firstDate(aggregate.firstDate())
                .lastDate(aggregate.lastDate())
                .totalCalories(round(aggregate.totalCalories()))
                .totalFat(round(aggregate.totalFat()))
                .totalCarbs(round(aggregate.totalCarbs()))
                .totalProtein(round(aggregate.totalProtein()))
                .averageCalories(round(aggregate.totalCalories() / occurrences.size()))
                .weekdayDistribution(aggregate.weekdayDistribution())
                .build();
    }

    /**
     * Intermediate result of a single pass over product occurrences, before it's shaped into
     * a {@link ProductSummaryDTO}.
     */
    private record ProductOccurrenceAggregate(List<String> sortedNames, Map<DayOfWeek, Long> weekdayDistribution,
                                              LocalDate firstDate, LocalDate lastDate,
                                              double totalCalories, double totalFat,
                                              double totalCarbs, double totalProtein) {
    }

    private ProductOccurrenceAggregate aggregateOccurrences(List<ProductWithDate> occurrences) {
        LinkedHashSet<String> matchedNames = new LinkedHashSet<>();
        Map<DayOfWeek, Long> weekdayDistribution = new EnumMap<>(DayOfWeek.class);
        LocalDate firstDate = null;
        LocalDate lastDate = null;
        double totalCalories = 0;
        double totalFat = 0;
        double totalCarbs = 0;
        double totalProtein = 0;

        for (ProductWithDate occurrence : occurrences) {
            if (occurrence.getProduct() != null) {
                matchedNames.add(occurrence.getProduct().getName());
                totalCalories += occurrence.getProduct().getCalories();
                totalFat += occurrence.getProduct().getFat();
                totalCarbs += occurrence.getProduct().getCarbs();
                totalProtein += occurrence.getProduct().getProtein();
            }
            LocalDate date = occurrence.getDate();
            if (date != null) {
                firstDate = firstDate == null || date.isBefore(firstDate) ? date : firstDate;
                lastDate = lastDate == null || date.isAfter(lastDate) ? date : lastDate;
                weekdayDistribution.merge(date.getDayOfWeek(), 1L, Long::sum);
            }
        }

        List<String> sortedNames = new ArrayList<>(matchedNames);
        sortedNames.sort(Comparator.naturalOrder());

        return new ProductOccurrenceAggregate(sortedNames, weekdayDistribution, firstDate, lastDate,
                totalCalories, totalFat, totalCarbs, totalProtein);
    }

    /**
     * Ranks products by how often they were logged or by the nutrient totals they contributed.
     *
     * @param ranking  the criterion to rank by
     * @param fromDate the earliest date to include, or null for no lower bound
     * @param toDate   the latest date to include, or null for no upper bound
     * @param limit    the maximum number of products to return
     * @return the ranked products, highest first
     */
    public List<TopProductDTO> getTopProducts(ProductRanking ranking, LocalDate fromDate, LocalDate toDate, int limit) {
        List<AggregationOperation> operations = new ArrayList<>();

        addDateRangeMatch(operations, fromDate, toDate);
        operations.add(unwind("products"));
        operations.add(group("products.name")
                .count().as("timesEaten")
                .sum("products.calories").as("totalCalories")
                .sum("products.fat").as("totalFat")
                .sum("products.carbs").as("totalCarbs")
                .sum("products.protein").as("totalProtein")
                .avg("products.calories").as("averageCalories"));
        operations.add(project("timesEaten", "totalCalories", "totalFat", "totalCarbs", "totalProtein", "averageCalories")
                .and("_id").as("name")
                .andExclude("_id"));
        operations.add(sort(Sort.Direction.DESC, ranking.getFieldName()));
        operations.add(limit(limit));

        AggregationResults<TopProductDTO> results = mongoTemplate.aggregate(
                newAggregation(operations), COLLECTION_NAME, TopProductDTO.class);

        return results.getMappedResults().stream()
                .map(this::round)
                .toList();
    }

    /**
     * Lists the distinct product names in the database, so callers can resolve fuzzy wording
     * ("oats") to the exact, brand-prefixed name FDDB stores ("Haferflocken kernig").
     *
     * @param search an optional case-insensitive substring the name has to contain
     * @param limit  the maximum number of names to return
     * @return the matching names in alphabetical order
     */
    public List<String> findDistinctProductNames(String search, int limit) {
        List<AggregationOperation> operations = new ArrayList<>();

        operations.add(unwind("products"));
        if (search != null && !search.isBlank()) {
            operations.add(match(Criteria.where("products.name").regex(search, "i")));
        }
        operations.add(group("products.name"));
        operations.add(sort(Sort.Direction.ASC, "_id"));
        operations.add(limit(limit));

        AggregationResults<Document> results = mongoTemplate.aggregate(
                newAggregation(operations), COLLECTION_NAME, Document.class);

        return results.getMappedResults().stream()
                .map(document -> document.getString("_id"))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<ProductWithDate> findByProductsWithExclusions(List<String> includeNames, List<String> excludeNames, LocalDate startDate) {
        List<AggregationOperation> operations = new ArrayList<>();

        // Add date filter first
        if (startDate != null) {
            operations.add(match(Criteria.where("date").gte(startDate)));
        }

        // Unwind products array
        operations.add(unwind("products"));

        // Match stage after unwind to filter individual products
        if (!includeNames.isEmpty()) {
            List<Criteria> includeList = includeNames.stream()
                    .map(name -> Criteria.where("products.name").regex(name, "i"))
                    .toList();
            operations.add(match(new Criteria().orOperator(includeList.toArray(new Criteria[0]))));
        }

        if (!excludeNames.isEmpty()) {
            List<Criteria> excludeList = excludeNames.stream()
                    .map(name -> Criteria.where("products.name").regex(name, "i"))
                    .toList();
            operations.add(match(new Criteria().norOperator(excludeList.toArray(new Criteria[0]))));
        }

        // Project required fields
        operations.add(project()
                .andExpression("date").as("date")
                .and("products").as("product"));

        Aggregation aggregation = newAggregation(operations);

        AggregationResults<ProductWithDate> results = mongoTemplate.aggregate(
                aggregation, COLLECTION_NAME, ProductWithDate.class);

        return results.getMappedResults();
    }

    /**
     * Unwinds the products array and returns every single occurrence of a product matching the
     * given name, newest first. Shared by the product search and the product summary.
     */
    private List<ProductWithDate> findProductOccurrences(String name, LocalDate fromDate, LocalDate toDate) {
        List<AggregationOperation> operations = new ArrayList<>();

        addDateRangeMatch(operations, fromDate, toDate);
        // match before unwinding so days without the product never get expanded
        operations.add(match(Criteria.where("products.name").regex(name, "i")));
        operations.add(unwind("products"));
        operations.add(match(Criteria.where("products.name").regex(name, "i")));
        operations.add(sort(Sort.Direction.DESC, "date"));
        operations.add(project()
                .andExpression("date").as("date")
                .and("products").as("product"));

        AggregationResults<ProductWithDate> results = mongoTemplate.aggregate(
                newAggregation(operations), COLLECTION_NAME, ProductWithDate.class);

        return results.getMappedResults();
    }

    private void addDateRangeMatch(List<AggregationOperation> operations, LocalDate fromDate, LocalDate toDate) {
        Criteria criteria = buildDateCriteria(fromDate, toDate);
        if (criteria != null) {
            operations.add(match(criteria));
        }
    }

    private Criteria buildDateCriteria(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return null;
        }
        Criteria criteria = Criteria.where("date");
        if (fromDate != null) {
            criteria = criteria.gte(fromDate);
        }
        if (toDate != null) {
            criteria = criteria.lte(toDate);
        }
        return criteria;
    }

    private TopProductDTO round(TopProductDTO product) {
        product.setTotalCalories(round(product.getTotalCalories()));
        product.setTotalFat(round(product.getTotalFat()));
        product.setTotalCarbs(round(product.getTotalCarbs()));
        product.setTotalProtein(round(product.getTotalProtein()));
        product.setAverageCalories(round(product.getAverageCalories()));
        return product;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

}
