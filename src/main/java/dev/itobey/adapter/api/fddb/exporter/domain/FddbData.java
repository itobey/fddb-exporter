package dev.itobey.adapter.api.fddb.exporter.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document(collection = "fddb")
@Data
public class FddbData {

    @Id
    private String id;
    private Date date;
    private List<Product> products;
    private double totalCalories;
    private double totalFat;
    private double totalCarbs;
    private double totalSugar;
    private double totalProtein;
    private double totalFibre;

}
