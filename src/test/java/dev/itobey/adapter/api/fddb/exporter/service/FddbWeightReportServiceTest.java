package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbApi;
import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMeasurement;
import dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendGranularity;
import dev.itobey.adapter.api.fddb.exporter.dto.TrendPointDTO;
import dev.itobey.adapter.api.fddb.exporter.exception.ParseException;
import dev.itobey.adapter.api.fddb.exporter.service.persistence.BodyMeasurementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.itobey.adapter.api.fddb.exporter.domain.BodyMetric.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FddbWeightReportServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private final FddbApi api = mock(FddbApi.class);
    private final BodyMeasurementStore store = mock(BodyMeasurementStore.class);
    private final FddbWeightReportService service = new FddbWeightReportService(api, store, new FddbExporterProperties());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Date|Weight|Body fat percentage|Body water|Waist circumference|Hip circumference|.",
            "Datum|Gewicht|Körperfett|Wasseranteil|Taillenumfang|Hüftumfang|,"
    })
    void importsLocalizedRowsWithNestedActionTables(String date, String weight, String fat,
                                                      String water, String waist, String hip, String decimal) {
        when(api.getWeightReport()).thenReturn("""
                <table class="mydata-history">
                  <tr><th>%s</th><th>%s</th><th>%s</th><th>%s</th><th>%s</th><th>%s</th><th colspan="2">Actions</th></tr>
                  <tr><td>08.01.2026</td><td>74@2 kg</td><td>19@4 %%</td><td>53@1 %%</td><td>80@5 cm</td><td>95@2 cm</td>
                    <td><table><tr><td><a href="/db/i18n/mydata/?lang=en&amp;action=edit&amp;q=123">Edit</a></td></tr></table></td>
                  </tr>
                  <tr><td>09.01.2026</td><td>74 kg</td><td>—</td><td>-</td><td></td><td>-</td>
                    <td><a href="/db/i18n/mydata/?lang=en&amp;action=edit&amp;q=124">Edit</a></td>
                  </tr>
                </table>
                """.formatted(date, weight, fat, water, waist, hip).replace("@", decimal));
        var first = reading("123", LocalDate.of(2026, 1, 8),
                Map.of(WEIGHT_KG, 74.2, BODY_FAT_PERCENT, 19.4, BODY_WATER_PERCENT, 53.1, WAIST_CM, 80.5, HIP_CM, 95.2));
        var second = reading("124", LocalDate.of(2026, 1, 9), Map.of(WEIGHT_KG, 74.0));
        var columns = Set.of(WEIGHT_KG, BODY_FAT_PERCENT, BODY_WATER_PERCENT, WAIST_CM, HIP_CM);
        when(store.updateMeasurements(List.of(first, second), columns)).thenReturn(2);

        assertThat(service.sync()).isEqualTo(2);
        verify(store).updateMeasurements(List.of(first, second), columns);
    }

    @Test
    void rejectsAnUnexpectedPageBeforeWriting() {
        when(api.getWeightReport()).thenReturn("<html><form>Log in</form></html>");
        assertThatThrownBy(service::sync).isInstanceOf(ParseException.class);
        verifyNoInteractions(store);
    }

    @Test
    void skipsMissingMetricsAndAveragesReadingsByDayBeforeGroupingByWeek() {
        LocalDate from = LocalDate.of(2025, 12, 29);
        LocalDate to = from.plusDays(6);
        when(store.findHistory(from.atStartOfDay(ZONE).toInstant(),
                to.plusDays(1).atStartOfDay(ZONE).toInstant(), "fddb")).thenReturn(List.of(
                reading("1", from, Map.of(BODY_FAT_PERCENT, 18.0)),
                reading("2", from, Map.of(BODY_FAT_PERCENT, 22.0)),
                reading("3", from.plusDays(1), Map.of(WEIGHT_KG, 73.0)),
                reading("4", to, Map.of(BODY_FAT_PERCENT, 18.0))));

        assertThat(service.getTrend(BODY_FAT_PERCENT, from, to, TrendGranularity.WEEK))
                .containsExactly(TrendPointDTO.builder().bucket("2026-W01").fromDate(from).toDate(to)
                        .dayCount(2).average(19).total(38).build());
    }

    private static BodyMeasurement reading(String id, LocalDate day, Map<BodyMetric, Double> metrics) {
        return new BodyMeasurement("fddb", id, day.atStartOfDay(ZONE).toInstant(), metrics);
    }
}
