package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
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
        TimeframeDTO timeframeDTO = timeframeCalculator.calculateTimeframeForYesterday();
        // then
        assertTrue(timeframeDTO.getFrom() < timeframeDTO.getTo());
    }

    @Test
    public void calculateTimeframeFor_shouldCreateTimeframeObject() {
        // given
        TimeframeCalculator timeframeCalculator = new TimeframeCalculator();
        // when
        TimeframeDTO timeframeDTO = timeframeCalculator.calculateTimeframeFor(LocalDate.now());
        // then
        assertTrue(timeframeDTO.getFrom() < timeframeDTO.getTo());
    }

}
