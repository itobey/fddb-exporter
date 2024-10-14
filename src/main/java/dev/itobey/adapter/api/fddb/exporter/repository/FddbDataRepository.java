package dev.itobey.adapter.api.fddb.exporter.repository;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.Optional;

@ConditionalOnProperty(name = "fddb-exporter.persistence.mongodb.enabled", havingValue = "true")
public interface FddbDataRepository extends MongoRepository<FddbData, String> {

    Optional<FddbData> findFirstByDate(LocalDate date);

}
