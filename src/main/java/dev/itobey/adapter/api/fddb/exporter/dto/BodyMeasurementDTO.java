package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(name = "BodyMeasurement")
public record BodyMeasurementDTO(String source, String externalId, Instant measuredAt, Map<String, Double> metrics) {
}
