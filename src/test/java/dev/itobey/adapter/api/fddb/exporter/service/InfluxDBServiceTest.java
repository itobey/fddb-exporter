package dev.itobey.adapter.api.fddb.exporter.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.exceptions.InfluxException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfluxDBServiceTest {

    private static final String MEASUREMENT = "testMeasurement";
    private static final String FIELD = "testField";
    private static final double VALUE = 42.0;

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private WriteApi writeApi;

    @Captor
    private ArgumentCaptor<Point> pointCaptor;

    @InjectMocks
    private InfluxDBService influxDBService;

    @BeforeEach
    void setUp() {
        when(influxDBClient.makeWriteApi()).thenReturn(writeApi);
    }

    @Test
    void writeData_shouldWritePointToInfluxDB() {
        // given
        Instant time = Instant.now();

        // when
        influxDBService.writeData(FIELD, VALUE, time);

        // then
        verify(influxDBClient, times(1)).makeWriteApi();
        verify(writeApi, times(1)).writePoint(pointCaptor.capture());
        verify(writeApi, times(1)).close();

        Point capturedPoint = pointCaptor.getValue();
        String lineProtocol = capturedPoint.toLineProtocol();
        String[] split = lineProtocol.split(" ");
        assert capturedPoint.getTime() != null;
        Instant capturedTime = Instant.ofEpochSecond(0, capturedPoint.getTime().longValue());
        assertThat(split[0]).isEqualTo(MEASUREMENT);
        assertThat(split[1]).isEqualTo(FIELD + "=" + VALUE);
        assertThat(capturedTime).isEqualTo(time);
        assertThat(capturedPoint.getPrecision()).isEqualTo(WritePrecision.NS);
    }

    @Test
    void writeData_shouldThrowExceptionWhenWriteFails() {
        // given
        doThrow(new InfluxException("Write failed")).when(writeApi).writePoint(any(Point.class));

        // when/then
        assertThatThrownBy(() -> influxDBService.writeData(FIELD, VALUE, Instant.now()))
                .isInstanceOf(InfluxException.class)
                .hasMessage("Write failed");
    }
}
