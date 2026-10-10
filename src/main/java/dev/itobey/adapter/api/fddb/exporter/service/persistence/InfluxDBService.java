package dev.itobey.adapter.api.fddb.exporter.service.persistence;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxTable;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Service class for persisting data to InfluxDB.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fddb-exporter.persistence.influxdb.enabled", havingValue = "true")
public class InfluxDBService {

    public static final String DAILY_TOTALS = "dailyTotals";

    private final InfluxDBClient influxDBClient;

    private final FddbExporterProperties properties;

    /**
     * Saves the given FddbData object to InfluxDB - but only uses the total values of the FddbData object.
     * The diary entries are not saved, because InfluxDB is not a document database.
     *
     * @param fddbData The FddbData object containing the data to be saved.
     */
    public void saveToInfluxDB(FddbData fddbData) {
        Instant time = fddbData.getDate().atStartOfDay(ZoneId.systemDefault()).toInstant();

        Point point = Point.measurement(DAILY_TOTALS)
                .addField("calories", fddbData.getTotalCalories())
                .addField("fat", fddbData.getTotalFat())
                .addField("carbs", fddbData.getTotalCarbs())
                .addField("sugar", fddbData.getTotalSugar())
                .addField("protein", fddbData.getTotalProtein())
                .addField("fibre", fddbData.getTotalFibre())
                .time(time, WritePrecision.NS);

        influxDBClient.getWriteApiBlocking().writePoint(point);
    }

    /**
     * Returns the amount of data points in the database, similar to the "count" function in SQL.
     *
     * @return the amount of data points in the database.
     */
    public long getDataPointCount() {
        QueryApi queryApi = influxDBClient.getQueryApi();
        String flux = "from(bucket:\"" + properties.getInfluxdb().getBucket() + "\")" +
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

