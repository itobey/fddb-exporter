package dev.itobey.adapter.api.fddb.exporter.dto.correlation;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CorrelationOutputDto {

    private Correlations correlations;
    private List<String> matchedProducts;
    private List<LocalDate> matchedDates;
    private int totalEntriesInDatabase;

}
