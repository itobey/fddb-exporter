package dev.itobey.adapter.api.fddb.exporter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A consumed product with nutritional information")
public class ProductDTO {

    @Schema(description = "Product name", example = "Banana")
    private String name;

    @Schema(description = "Amount consumed", example = "100 g")
    private String amount;

    @Schema(description = "Calories", example = "89.0")
    private double calories;

    @Schema(description = "Fat in grams", example = "0.3")
    private double fat;

    @Schema(description = "Carbs in grams", example = "23.0")
    private double carbs;

    @Schema(description = "Protein in grams", example = "1.1")
    private double protein;

    @Schema(description = "Link to product page on FDDB", example = "https://fddb.info/db/de/lebensmittel/...")
    private String link;

    // default to "0 g" if the amount is null, to have a consistent API.
    public String getAmount() {
        return amount == null ? "0 g" : amount;
    }

}
