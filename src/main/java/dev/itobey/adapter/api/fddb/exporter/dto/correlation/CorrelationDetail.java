package dev.itobey.adapter.api.fddb.exporter.dto.correlation;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

@Data
public class CorrelationDetail {

    private static final DecimalFormat df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));


    @Setter(AccessLevel.NONE)
    private double percentage;
    private List<String> matchedDates;
    private int matchedDays;

    public void setPercentage(double percentage) {
        this.percentage = Double.parseDouble(df.format(percentage));
    }

}
