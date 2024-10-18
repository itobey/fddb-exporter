package dev.itobey.adapter.api.fddb.exporter.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Document(collection = "fddb")
@Data
public class FddbData {

    @Id
    @EqualsAndHashCode.Exclude
    private String id;
    private LocalDate date;
    private List<Product> products;
    private double totalCalories;
    private double totalFat;
    private double totalCarbs;
    private double totalSugar;
    private double totalProtein;
    private double totalFibre;

    public String toDailyTotalsString() {
        return "FddbData{" +
                "date=" + date +
                ", totalCalories=" + totalCalories +
                ", totalFat=" + totalFat +
                ", totalCarbs=" + totalCarbs +
                ", totalSugar=" + totalSugar +
                ", totalProtein=" + totalProtein +
                ", totalFibre=" + totalFibre +
                '}';
    }

}
