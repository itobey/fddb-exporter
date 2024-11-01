package dev.itobey.adapter.api.fddb.exporter.dto.correlation;

import lombok.Data;

import java.util.List;

@Data
public class CorrelationDetail {

    private double percentage;
    private List<String> matchedDates;

}
