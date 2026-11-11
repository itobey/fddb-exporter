package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.exception.GlobalExceptionHandler;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.BodyMeasurementStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BodyMeasurementResourceV2Test {
    private static final String PATH = "/api/v2/body-measurements";
    private static final Instant MEASURED_AT = Instant.parse("2026-01-01T23:15:00.123Z");

    private final BodyMeasurementStore store = mock(BodyMeasurementStore.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BodyMeasurementResourceV2(store))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void importsNormalizedJsonAndListsMeasurementsWithSourceAndTimeBounds() throws Exception {
        var measurement = new BodyMeasurement("withings", "abc-123", MEASURED_AT, Map.of(WEIGHT_KG, 82.5));
        when(store.importMeasurements(List.of(measurement))).thenReturn(1);
        mvc.perform(post(PATH).contentType("application/json").content("""
                [{"source":"withings","externalId":"abc-123","measuredAt":"2026-01-02T00:15:00.123+01:00",
                  "metrics":{"WEIGHT_KG":82.5}}]
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));

        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        when(store.find(from, to, "withings", 1, 25)).thenReturn(List.of(measurement));
        mvc.perform(get(PATH).param("source", "withings").param("from", from.toString())
                        .param("to", to.toString()).param("page", "1").param("size", "25"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].externalId").value("abc-123"))
                .andExpect(jsonPath("$[0].metrics.WEIGHT_KG").value(82.5));
    }

    @Test
    void rejectsUnsupportedMetricInJson() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content("""
                [{"source":"withings","externalId":"abc-123","measuredAt":"2026-01-01T23:15:00Z",
                  "metrics":{"BODY_FAT":0.24}}]
                """)).andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }

}
