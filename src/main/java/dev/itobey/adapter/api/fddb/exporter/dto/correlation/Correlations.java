package dev.itobey.adapter.api.fddb.exporter.dto.correlation;

import lombok.Data;

@Data
public class Correlations {

    private CorrelationDetail across3Days;
    private CorrelationDetail across2Days;
    private CorrelationDetail sameDay;
    private CorrelationDetail oneDayBefore;
    private CorrelationDetail twoDaysBefore;

}
