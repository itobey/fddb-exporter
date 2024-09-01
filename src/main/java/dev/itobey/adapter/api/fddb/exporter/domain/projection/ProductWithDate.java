package dev.itobey.adapter.api.fddb.exporter.domain.projection;

import dev.itobey.adapter.api.fddb.exporter.domain.Product;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductWithDate {

    private LocalDate date;
    private Product product;

}
