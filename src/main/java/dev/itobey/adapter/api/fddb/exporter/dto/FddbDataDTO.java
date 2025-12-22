package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "FDDB nutrition data for a specific day")
public class FddbDataDTO {

    @Schema(description = "Unique identifier", example = "507f1f77bcf86cd799439011")
    private String id;

    @Schema(description = "Date of the entry", example = "2024-12-22")
    private LocalDate date;

    @Schema(description = "List of products consumed on this day")
    private List<ProductDTO> products;

    @Schema(description = "Total calories for the day", example = "2000.5")
    private double totalCalories;

    @Schema(description = "Total fat in grams", example = "65.3")
    private double totalFat;

    @Schema(description = "Total carbs in grams", example = "250.2")
    private double totalCarbs;

    @Schema(description = "Total sugar in grams", example = "50.1")
    private double totalSugar;

    @Schema(description = "Total protein in grams", example = "80.4")
    private double totalProtein;

    @Schema(description = "Total fibre in grams", example = "25.6")
    private double totalFibre;

}
