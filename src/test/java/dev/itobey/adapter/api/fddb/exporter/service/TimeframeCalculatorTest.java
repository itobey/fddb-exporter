package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization test for @{@link TimeframeCalculator}.
 * <p>
 * This class decides which 24 hours of FDDB data get attributed to which calendar day, so these tests pin the
 * exact epoch-second boundaries the current implementation produces rather than just their ordering: a summer
 * date (CEST, UTC+2), a winter date (CET, UTC+1) and both DST transition Sundays. The DST cases document
 * the current overlap and gap behaviour so changes to these boundaries are visible and deliberate.
 */
public class TimeframeCalculatorTest {

    private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

    private final TimeframeCalculator timeframeCalculator = new TimeframeCalculator();

    @Test
    public void calculateTimeframeFor_summerDate_shouldSpan0200CestToNextDay0159() {
        // given
        LocalDate summerDate = LocalDate.of(2024, 7, 15);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeFor(summerDate);

        // then: 2024-07-15T02:00+02:00 (CEST) == 1721001600, end is 24h later minus one second
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

        // then: 2024-01-15T02:00+01:00 (CET) == 1705280400, end is 24h later minus one second
        assertEquals(1705280400L, timeframe.getFrom());
        assertEquals(1705366799L, timeframe.getTo());
        assertEquals(86399L, timeframe.getTo() - timeframe.getFrom());
    }

    /**
     * Spring-forward Sunday: at 02:00 CET the clock jumps to 03:00 CEST, so 02:00 local does not exist.
     * <p>
     * {@code atStartOfDay().plusHours(2)} adds two
     * <em>absolute</em> hours, which lands on 03:00 CEST instead of the nominal 02:00 boundary, and
     * {@code plusHours(24)} adds 24 absolute hours, so the window ends at 03:00 CEST the next day. The window
     * is therefore shifted one hour later than on any other day and overlaps the following day's window by
     * one hour.
     */
    @Test
    public void calculateTimeframeFor_springForwardSunday_shouldUseAbsoluteHoursAndShiftTheWindow() {
        // given
        LocalDate springForward = LocalDate.of(2024, 3, 31);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeFor(springForward);

        // then: start is 2024-03-31T03:00+02:00 (CEST) == 1711846800, i.e. 03:00 local, not 02:00
        assertEquals(1711846800L, timeframe.getFrom());
        // end is 2024-04-01T03:00+02:00 minus one second == 1711933199
        assertEquals(1711933199L, timeframe.getTo());
        // still exactly 24 absolute hours minus one second, unlike the 23-hour local day
        assertEquals(86399L, timeframe.getTo() - timeframe.getFrom());
    }

    /**
     * Autumn-back Sunday: at 03:00 CEST the clock falls back to 02:00 CET, so the local day is 25 hours long.
     * <p>
     * The start is the nominal 02:00 CEST, but
     * {@code plusHours(24)} adds 24 <em>absolute</em> hours into a 25-hour local day, so the window ends at
     * 01:00 CET the next day — one hour short of the nominal boundary, leaving a one-hour gap before the
     * following day's window.
     */
    @Test
    public void calculateTimeframeFor_autumnBackSunday_shouldUseAbsoluteHoursAndEndAnHourEarly() {
        // given
        LocalDate autumnBack = LocalDate.of(2024, 10, 27);

        // when
        TimeframeDTO timeframe = timeframeCalculator.calculateTimeframeFor(autumnBack);

        // then: start is 2024-10-27T02:00+02:00 (CEST) == 1729987200
        assertEquals(1729987200L, timeframe.getFrom());
        // end is 2024-10-28T01:00+01:00 (CET) minus one second == 1730073599, not 02:00 local
        assertEquals(1730073599L, timeframe.getTo());
        // still exactly 24 absolute hours minus one second, unlike the 25-hour local day
        assertEquals(86399L, timeframe.getTo() - timeframe.getFrom());
    }

    /**
     * {@code calculateTimeframeForYesterday()} reads {@code LocalDate.now(Europe/Berlin)} internally and takes
     * no clock, so this asserts the relationship to "yesterday in Berlin" rather than a literal. The date is
     * sampled before and after the call so a run that crosses midnight cannot flake.
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
