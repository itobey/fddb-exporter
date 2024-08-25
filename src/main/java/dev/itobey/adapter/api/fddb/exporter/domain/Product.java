package dev.itobey.adapter.api.fddb.exporter.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private String name;
    private String amount;
    private double calories;
    private double fat;
    private double carbs;
    private double protein;
    private String link;
}
