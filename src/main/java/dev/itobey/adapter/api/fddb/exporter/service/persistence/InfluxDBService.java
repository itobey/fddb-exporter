package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InfluxDBService {

    public static final String DAILY_TOTALS = "dailyTotals";

    private final InfluxDBClient influxDBClient;

    public void saveToInfluxDB(FddbData fddbData) {
        Instant time = fddbData.getDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Map<String, Double> metrics = Map.of(
                "calories", fddbData.getTotalCalories(),
                "fat", fddbData.getTotalFat(),
                "carbs", fddbData.getTotalCarbs(),
                "sugar", fddbData.getTotalSugar(),
                "fibre", fddbData.getTotalFibre(),
                "protein", fddbData.getTotalProtein()
        );

        metrics.forEach((metric, value) ->
                writeData(metric, value, time)
        );
    }

    public void writeData(String field, double value, Instant time) {
        try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
            Point point = Point.measurement(DAILY_TOTALS)
                    .addField(field, value)
                    .time(time, WritePrecision.NS);

            writeApi.writePoint(point);
        }
    }
}

