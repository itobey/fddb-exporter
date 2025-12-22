package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Contains rolling averages for a given date range.
 * Used for statistics reporting.
 */
@Data
@Builder
public class RollingAveragesDTO {
    String fromDate;
    String toDate;
    StatsDTO.Averages averages;
}

