package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.exception.GlobalExceptionHandler;
import dev.itobey.adapter.api.fddb.exporter.service.FddbWeightReportService;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.BodyMeasurementStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric.WEIGHT_KG;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BodyMeasurementResourceV2Test {
    private static final String PATH = "/api/v2/body-measurements";
    private static final Instant MEASURED_AT = Instant.parse("2026-01-01T23:15:00.123Z");

    private final BodyMeasurementStore store = mock(BodyMeasurementStore.class);
    private final FddbWeightReportService weightReport = mock(FddbWeightReportService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BodyMeasurementResourceV2(store, weightReport))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listsMeasurementsWithTimeBoundsAndPagination() throws Exception {
        var measurement = new BodyMeasurement("fddb", "123", MEASURED_AT, Map.of(WEIGHT_KG, 82.5));
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        when(store.find(from, to, null, 1, 25)).thenReturn(List.of(measurement));
        mvc.perform(get(PATH).param("from", from.toString())
                        .param("to", to.toString()).param("page", "1").param("size", "25"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].source").value("fddb"))
                .andExpect(jsonPath("$[0].externalId").value("123"))
                .andExpect(jsonPath("$[0].measuredAt").value(MEASURED_AT.toString()))
                .andExpect(jsonPath("$[0].metrics.WEIGHT_KG").value(82.5));
    }

    @Test
    void returnsBodyMeasurementTrendWithNoCaching() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(2);
        when(weightReport.getTrend(WEIGHT_KG, from, to, TrendGranularity.WEEK))
                .thenReturn(List.of(TrendPointDTO.builder().bucket("2026-W01").dayCount(1).average(82.5).build()));

        mvc.perform(get(PATH + "/trend").param("metric", "WEIGHT_KG").param("fromDate", from.toString())
                        .param("toDate", to.toString()).param("granularity", "WEEK"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].bucket").value("2026-W01"))
                .andExpect(jsonPath("$[0].average").value(82.5));
    }

    @Test
    void keepsTheMeasurementApiReadOnly() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(store);
    }

}
