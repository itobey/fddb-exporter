package com.itobey.adapter.api.fddb.exporter;

import com.itobey.adapter.api.fddb.exporter.domain.Timeframe;
import com.itobey.adapter.api.fddb.exporter.service.TimeframeCalculator;
import org.junit.jupiter.api.Test;

public class TimeframeCalculatorTest {

    @Test
    public void test() {
        TimeframeCalculator timeframeCalculator = new TimeframeCalculator();
        Timeframe timeframe = timeframeCalculator.calculateTimeframeForYesterday();
        System.out.println(timeframe);
    }

}
