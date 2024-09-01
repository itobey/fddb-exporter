package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FddbDataDTO {

    private String id;
    private LocalDate date;
    private List<ProductDTO> products;
    private double totalCalories;
    private double totalFat;
    private double totalCarbs;
    private double totalSugar;
    private double totalProtein;
    private double totalFibre;

}
