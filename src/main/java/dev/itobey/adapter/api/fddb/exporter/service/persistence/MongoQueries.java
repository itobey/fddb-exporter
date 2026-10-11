package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import org.springframework.data.mongodb.core.query.Criteria;

import java.time.LocalDate;

/**
 * Shared diary collection and date-range criteria.
 */
final class MongoQueries {

    static final String COLLECTION_NAME = "fddb";

    private MongoQueries() {
    }

    /**
     * Builds the {@code date} criteria for a range, both bounds inclusive and either of them optional.
     *
     * @param fromDate the first date to include, or null for no lower bound
     * @param toDate   the last date to include, or null for no upper bound
     * @return the criteria, or null if the range is unbounded on both sides
     */
    static Criteria buildDateCriteria(LocalDate fromDate, LocalDate toDate) {
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
}
