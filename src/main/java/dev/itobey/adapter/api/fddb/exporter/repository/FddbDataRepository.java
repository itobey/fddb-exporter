package dev.itobey.adapter.api.fddb.exporter.repository;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public interface FddbDataRepository extends MongoRepository<FddbData, String> {

    Optional<FddbData> findFirstByDate(LocalDate date);

    /**
     * Retrieves all entries between two dates, both bounds inclusive, oldest first.
     * <p>
     * Declared as an explicit query rather than derived from the method name: the {@code Between}
     * keyword maps to {@code $gt}/{@code $lt} and would silently drop both boundary days, while
     * spelling it out as {@code GreaterThanEqual}/{@code LessThanEqual} produces two conflicting
     * criteria on the same field.
     *
     * @param fromDate the first date to include
     * @param toDate   the last date to include
     * @return the matching entries ordered by date ascending
     */
    @Query(value = "{ 'date': { $gte: ?0, $lte: ?1 } }", sort = "{ 'date': 1 }")
    List<FddbData> findInDateRange(LocalDate fromDate, LocalDate toDate);

    /**
     * Retrieves the most recent entry, regardless of whether it has any products.
     *
     * @return an Optional of the newest {@link FddbData}
     */
    Optional<FddbData> findFirstByOrderByDateDesc();

}
