package dev.itobey.adapter.api.fddb.exporter.dto;

import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductWithDateDTO {

    private LocalDate date;
    private Product product;

}
