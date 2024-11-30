package dev.itobey.adapter.api.fddb.exporter.dto.correlation;

import lombok.Data;

import java.util.List;

@Data
public class CorrelationInputDto {

    private List<String> inclusionKeywords;
    private List<String> exclusionKeywords;
    private List<String> occurrenceDates;
    private String startDate;

}
