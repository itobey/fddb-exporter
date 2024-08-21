package dev.itobey.adapter.api.fddb.exporter.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains the dates which should be processed in a batch export.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FddbBatchExport {

    private String fromDate;
    private String toDate;

}
