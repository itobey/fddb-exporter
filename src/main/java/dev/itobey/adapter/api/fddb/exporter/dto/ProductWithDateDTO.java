package dev.itobey.adapter.api.fddb.exporter.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductWithDateDTO {

    private LocalDate date;
    private ProductDTO product;

}
