package dev.itobey.adapter.api.fddb.exporter.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class InfluxDBService {

    private final InfluxDBClient influxDBClient;

    public void writeData(String measurement, String field, double value, Instant time) {
        try (WriteApi writeApi = influxDBClient.makeWriteApi()) {
            Point point = Point.measurement(measurement)
                    .addField(field, value)
                    .time(time, WritePrecision.NS);

            writeApi.writePoint(point);
        }
    }
}

