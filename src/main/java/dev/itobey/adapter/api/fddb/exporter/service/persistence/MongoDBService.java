package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.domain.projection.ProductWithDate;
import dev.itobey.adapter.api.fddb.exporter.repository.FddbDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Provides MongoDB-related services for managing {@link FddbData} objects.
 * <p>
 * This does not use Lomboks constructor, because the required=false is not supported by Lombok.
 */
@Service
@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public class MongoDBService {

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
     * Searches for a product name and returns the date with the product details.
     * Unfortunately an aggregation annotation query did not work, maybe because I'm stuck with Mongo 4.4.
     *
     * @param name the name of the product
     * @return a list of matches
     */
    public List<ProductWithDate> findByProduct(String name) {
        AggregationOperation match = match(Criteria.where("products.name").regex(name, "i"));
        AggregationOperation unwind = unwind("products");
        AggregationOperation secondMatch = match(Criteria.where("products.name").regex(name, "i"));
        AggregationOperation project = project()
                .andExpression("date").as("date")
                .and("products").as("product");

        Aggregation aggregation = newAggregation(match, unwind, secondMatch, project);

        AggregationResults<ProductWithDate> results = mongoTemplate.aggregate(
                aggregation, "fddb", ProductWithDate.class);

        return results.getMappedResults();
    }

    public List<ProductWithDate> findByProductsWithExclusions(List<String> includeNames, List<String> excludeNames) {
        List<AggregationOperation> operations = new ArrayList<>();

        // Unwind products array first
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
                aggregation, "fddb", ProductWithDate.class);

        return results.getMappedResults();
    }

}
