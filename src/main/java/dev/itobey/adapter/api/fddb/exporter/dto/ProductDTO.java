package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    private String name;
    private String amount;
    private double calories;
    private double fat;
    private double carbs;
    private double protein;
    private String link;

}
