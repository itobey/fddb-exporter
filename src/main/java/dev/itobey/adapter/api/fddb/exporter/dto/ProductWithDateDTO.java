package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A product with its consumption date")
public class ProductWithDateDTO {

    @Schema(description = "Date when the product was consumed", example = "2024-12-22")
    private LocalDate date;

    @Schema(description = "Product information")
    private ProductDTO product;

}
