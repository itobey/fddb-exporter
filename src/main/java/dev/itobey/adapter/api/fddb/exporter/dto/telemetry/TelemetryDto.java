package dev.itobey.adapter.api.fddb.exporter.dto.telemetry;

import dev.itobey.adapter.api.fddb.exporter.domain.ExecutionMode;
import lombok.Data;

@Data
public class TelemetryDto {

    private String mailHash;
    private long documentCount;
    private boolean mongodbEnabled;
    private boolean influxdbEnabled;
    private ExecutionMode executionMode;
    private String appVersion;

}
