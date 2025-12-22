package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of an export operation")
public class ExportResultDTO {
    @Schema(description = "Dates that were successfully exported", example = "[\"2024-12-20\", \"2024-12-21\"]")
    private List<String> successfulDays;

    @Schema(description = "Dates that failed to export", example = "[\"2024-12-22\"]")
    private List<String> unsuccessfulDays;
}