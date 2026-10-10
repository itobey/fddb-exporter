package dev.itobey.adapter.api.fddb.exporter.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.exceptions.InfluxException;
import dev.itobey.adapter.api.fddb.exporter.domain.FddbData;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.InfluxDBService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfluxDBServiceTest {

    private static final String MEASUREMENT = "dailyTotals";
    private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private WriteApiBlocking writeApi;

    @Captor
    private ArgumentCaptor<Point> pointCaptor;

    @InjectMocks
    private InfluxDBService influxDBService;

    @BeforeEach
    void setUp() {
        when(influxDBClient.getWriteApiBlocking()).thenReturn(writeApi);
    }

    @Test
    void saveToInfluxDB_shouldWriteASinglePointWithAllSixFields() {
        // given
        FddbData fddbData = createFddbData();

        // when
        influxDBService.saveToInfluxDB(fddbData);

        // then
        verify(influxDBClient, times(1)).getWriteApiBlocking();
        verify(writeApi, times(1)).writePoint(pointCaptor.capture());
        verifyNoMoreInteractions(writeApi);

        Point capturedPoint = pointCaptor.getValue();
        String[] lineProtocol = capturedPoint.toLineProtocol().split(" ");
        assertThat(lineProtocol[0]).isEqualTo(MEASUREMENT);

        Map<String, Double> fields = parseFields(lineProtocol[1]);
        assertThat(fields).hasSize(6)
                .containsEntry("calories", 2000.0)
                .containsEntry("fat", 70.5)
                .containsEntry("carbs", 210.25)
                .containsEntry("sugar", 45.75)
                .containsEntry("protein", 130.5)
                .containsEntry("fibre", 25.25);

        assertThat(capturedPoint.getTime()).isNotNull();
        Instant expectedTime = DATE.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant capturedTime = Instant.ofEpochSecond(0, capturedPoint.getTime().longValue());
        assertThat(capturedTime).isEqualTo(expectedTime);
        assertThat(capturedPoint.getPrecision()).isEqualTo(WritePrecision.NS);
    }

    @Test
    void saveToInfluxDB_shouldThrowExceptionWhenWriteFails() {
        // given
        doThrow(new InfluxException("Write failed")).when(writeApi).writePoint(any(Point.class));

        // when/then
        assertThatThrownBy(() -> influxDBService.saveToInfluxDB(createFddbData()))
                .isInstanceOf(InfluxException.class)
                .hasMessage("Write failed");
    }

    private FddbData createFddbData() {
        FddbData fddbData = new FddbData();
        fddbData.setDate(DATE);
        fddbData.setTotalCalories(2000.0);
        fddbData.setTotalFat(70.5);
        fddbData.setTotalCarbs(210.25);
        fddbData.setTotalSugar(45.75);
        fddbData.setTotalProtein(130.5);
        fddbData.setTotalFibre(25.25);
        return fddbData;
    }

    private Map<String, Double> parseFields(String fieldSet) {
        Map<String, Double> fields = new LinkedHashMap<>();
        Arrays.stream(fieldSet.split(","))
                .forEach(field -> {
                    String[] keyValue = field.split("=");
                    fields.put(keyValue[0], Double.parseDouble(keyValue[1]));
                });
        return fields;
    }
}
