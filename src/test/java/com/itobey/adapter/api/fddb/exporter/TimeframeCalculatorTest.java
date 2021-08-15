package com.itobey.adapter.api.fddb.exporter;

import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import com.itobey.adapter.api.fddb.exporter.service.TimeframeCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for @{@link TimeframeCalculator}
 * These are not highly useful, but well, better than nothing.
 */
public class TimeframeCalculatorTest {

    @Test
    public void calculateTimeframeForYesterday_shouldCreateTimeframeObject() {
        // given
        TimeframeCalculator timeframeCalculator = new TimeframeCalculator();
        // when
        Timeframe timeframe = timeframeCalculator.calculateTimeframeForYesterday();
        // then
        assertTrue(timeframe.getFrom() < timeframe.getTo());
    }

    @Test
    public void calculateTimeframeFor_shouldCreateTimeframeObject() {
        // given
        TimeframeCalculator timeframeCalculator = new TimeframeCalculator();
        // when
        Timeframe timeframe = timeframeCalculator.calculateTimeframeFor(LocalDate.now());
        // then
        assertTrue(timeframe.getFrom() < timeframe.getTo());
    }

}
