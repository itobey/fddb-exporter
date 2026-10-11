package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins exact scrape boundaries for summer, winter and DST transitions.
 */
public class TimeframeCalculatorTest {

    private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

    private final TimeframeCalculator timeframeCalculator = new TimeframeCalculator(berlinProperties());

    private static FddbExporterProperties berlinProperties() {
        FddbExporterProperties properties = new FddbExporterProperties();
        properties.setTimezone("Europe/Berlin");
        return properties;
    }

    @Test
    public void calculateTimeframeFor_summerDate_shouldSpan0200CestToNextDay0159() {
        // given
        LocalDate summerDate = LocalDate.of(2024, 7, 15);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeFor(summerDate);

        // 02:00 CEST through the next day's boundary minus one second.
        assertEquals(1721001600L, timeframe.getFrom());
        assertEquals(1721087999L, timeframe.getTo());
        assertEquals(86399L, timeframe.getTo() - timeframe.getFrom());
    }

    @Test
    public void calculateTimeframeFor_winterDate_shouldSpan0200CetToNextDay0159() {
        // given
        LocalDate winterDate = LocalDate.of(2024, 1, 15);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeFor(winterDate);

        // 02:00 CET through the next day's boundary minus one second.
        assertEquals(1705280400L, timeframe.getFrom());
        assertEquals(1705366799L, timeframe.getTo());
        assertEquals(86399L, timeframe.getTo() - timeframe.getFrom());
    }

    /**
     * Spring DST: 03:00 CEST to the next day's 02:00 CEST, spanning 23 hours without overlap.
     */
    @Test
    public void calculateTimeframeFor_springForwardSunday_shouldRunToTheNextLocal0200AndBe23Hours() {
        // given
        LocalDate springForward = LocalDate.of(2024, 3, 31);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeFor(springForward);

        // 02:00 falls in the DST gap, so the start is 03:00 CEST.
        assertEquals(1711846800L, timeframe.getFrom());
        // Next day's 02:00 CEST minus one second.
        assertEquals(1711929599L, timeframe.getTo());
        assertEquals(82799L, timeframe.getTo() - timeframe.getFrom());
    }

    /**
     * Autumn DST: the first 02:00 CEST to the next day's 02:00 CET, spanning 25 hours without a gap.
     */
    @Test
    public void calculateTimeframeFor_autumnBackSunday_shouldRunToTheNextLocal0200AndBe25Hours() {
        // given
        LocalDate autumnBack = LocalDate.of(2024, 10, 27);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeFor(autumnBack);

        // First occurrence of 02:00 (CEST).
        assertEquals(1729987200L, timeframe.getFrom());
        // Next day's 02:00 CET minus one second.
        assertEquals(1730077199L, timeframe.getTo());
        assertEquals(89999L, timeframe.getTo() - timeframe.getFrom());
    }

    /**
     * Adjacent windows have no gaps or overlaps across DST changes.
     */
    @Test
    public void calculateTimeframeFor_adjacentDays_shouldTileWithoutGapOrOverlapAcrossDstTransitions() {
        assertTilesInto(LocalDate.of(2024, 3, 30), LocalDate.of(2024, 3, 31));
        assertTilesInto(LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 1));
        assertTilesInto(LocalDate.of(2024, 10, 26), LocalDate.of(2024, 10, 27));
        assertTilesInto(LocalDate.of(2024, 10, 27), LocalDate.of(2024, 10, 28));
    }

    private void assertTilesInto(LocalDate first, LocalDate second) {
        TimeframeDTO earlier = timeframeCalculator.calculateTimeframeFor(first);
        TimeframeDTO later = timeframeCalculator.calculateTimeframeFor(second);

        assertEquals(earlier.getTo() + 1, later.getFrom(),
                "expected " + second + " to start the second after " + first + " ended");
    }

    /**
     * The configured timezone determines the scrape boundary.
     */
    @Test
    public void calculateTimeframeFor_configuredZone_shouldDecideTheBoundary() {
        // given
        FddbExporterProperties utcProperties = new FddbExporterProperties();
        utcProperties.setTimezone("UTC");
        TimeframeCalculator utcCalculator = new TimeframeCalculator(utcProperties);

        // when
        TimeframeDTO timeframe = utcCalculator.calculateTimeframeFor(LocalDate.of(2024, 7, 15));

        // 02:00 UTC, two hours later than Berlin.
        assertEquals(1721008800L, timeframe.getFrom());
        assertEquals(1721095199L, timeframe.getTo());
    }

    /**
     * Samples yesterday before and after the call to tolerate crossing midnight.
     */
    @Test
    public void calculateTimeframeForYesterday_shouldEqualTheTimeframeOfBerlinYesterday() {
        // given
        LocalDate yesterdayBefore = LocalDate.now(ZONE_BERLIN).minusDays(1);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeForYesterday();

        // then
        LocalDate yesterdayAfter = LocalDate.now(ZONE_BERLIN).minusDays(1);
        TimeframeDTO expectedBefore = timeframeCalculator.calculateTimeframeFor(yesterdayBefore);
        TimeframeDTO expectedAfter = timeframeCalculator.calculateTimeframeFor(yesterdayAfter);

        assertTrue(timeframe.equals(expectedBefore) || timeframe.equals(expectedAfter),
                "expected the timeframe of yesterday in Europe/Berlin (" + expectedBefore + " or "
                        + expectedAfter + ") but was " + timeframe);
    }

}
