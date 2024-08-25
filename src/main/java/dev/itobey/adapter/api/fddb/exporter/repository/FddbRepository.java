package dev.itobey.adapter.api.fddb.exporter.repository;

import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.Optional;

public interface FddbRepository extends MongoRepository<FddbData, String> {

    Optional<FddbData> findFirstByDate(Date date);

}
