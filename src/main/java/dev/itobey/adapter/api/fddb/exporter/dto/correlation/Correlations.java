package dev.itobey.adapter.api.fddb.exporter.dto.correlation;

import lombok.Data;

@Data
public class Correlations {

    private CorrelationDetail sameDay;
    private CorrelationDetail oneDayBefore;
    private CorrelationDetail twoDaysBefore;

}
