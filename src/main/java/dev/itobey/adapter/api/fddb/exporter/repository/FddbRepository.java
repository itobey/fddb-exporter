package dev.itobey.adapter.api.fddb.exporter.repository;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * The repository for persisting {@link FddbData}.
 */
@Repository
@Transactional
public interface FddbRepository extends CrudRepository<FddbData, Long> {

    /**
     * Retrieves the first entry to a specific date.
     *
     * @param date the date to search for
     * @return an Optional of {@link FddbData}
     */
    public Optional<FddbData> findFirstByDate(Date date);

}
