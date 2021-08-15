package com.itobey.adapter.api.fddb.exporter.repository;

import com.itobey.adapter.api.fddb.exporter.domain.FddbData;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The repository for persisting {@link FddbData}.
 */
@Repository
@Transactional
public interface FddbRepository extends CrudRepository<FddbData, Long> {
}
