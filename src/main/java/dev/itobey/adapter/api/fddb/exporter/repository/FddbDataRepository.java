package dev.itobey.adapter.api.fddb.exporter.repository;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FddbDataRepository extends MongoRepository<FddbData, String> {

    Optional<FddbData> findFirstByDate(LocalDate date);

    @Query("{ 'products.name': { $regex: ?0, $options: 'i' } }")
    List<FddbData> findByProductNameFuzzy(String name);

}
