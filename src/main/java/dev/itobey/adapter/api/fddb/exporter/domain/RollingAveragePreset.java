package dev.itobey.adapter.api.fddb.exporter.domain;

import lombok.Data;

import java.time.LocalDate;

/**
 * Embedded domain class representing a custom rolling average preset.
 * Stores a named date range that can be used as a quick-select button on the Rolling Averages view.
 */
@Data
public class RollingAveragePreset {

    private String name;
    private LocalDate fromDate;
    private LocalDate toDate;

}
