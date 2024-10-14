package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Service class for persisting data to InfluxDB.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fddb-exporter.persistence.influxdb.enabled", havingValue = "true")
public class InfluxDBService {

    public static final String DAILY_TOTALS = "dailyTotals";

    private final InfluxDBClient influxDBClient;

    @Value("${fddb-exporter.influxdb.bucket}")
    private String bucket;

    /**
     * Saves the given FddbData object to InfluxDB - but only uses the total values of the FddbData object.
     * The diary entries are not saved, because InfluxDB is not a document database.
     *
     * @param fddbData The FddbData object containing the data to be saved.
     */
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

    /**
     * Writes data as a Point to InfluxDB.
     *
     * @param field The field to be written.
     * @param value The value to be written.
     * @param time  The time of the data point.
     */
    public void writeData(String field, double value, Instant time) {
        try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
            Point point = Point.measurement(DAILY_TOTALS)
                    .addField(field, value)
                    .time(time, WritePrecision.NS);

            writeApi.writePoint(point);
        }
    }

    /**
     * Returns the amount of data points in the database, similar to the "count" function in SQL.
     *
     * @return the amount of data points in the database.
     */
    public long getDataPointCount() {
        QueryApi queryApi = influxDBClient.getQueryApi();
        String flux = "from(bucket:\"" + bucket + "\")" +
                " |> range(start: 0)" +
                " |> filter(fn: (r) => r._measurement == \"" + DAILY_TOTALS + "\")" +
                " |> count()";
        List<FluxTable> result = queryApi.query(flux);

        return result.stream()
                .flatMap(table -> table.getRecords().stream())
                .findFirst()
                .map(record -> record.getValue() instanceof Long ? (Long) record.getValue() : 0L)
                .orElse(0L);
    }

}

