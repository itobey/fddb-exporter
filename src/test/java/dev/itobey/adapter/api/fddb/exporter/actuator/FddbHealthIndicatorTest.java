package dev.itobey.adapter.api.fddb.exporter.actuator;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.service.FddbParserService;
import dev.itobey.adapter.api.fddb.exporter.service.TimeframeCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FddbHealthIndicatorTest {

    @InjectMocks
    private FddbHealthIndicator fddbHealthIndicator;

    @Mock
    private FddbParserService fddbParserService;
    @Mock
    private FddbAdapter fddbAdapter;
    @Mock
    private TimeframeCalculator timeframeCalculator;

    @Test
    void health_shouldReportUp_whenAuthenticationIsValid() {
        // given
        when(timeframeCalculator.calculateTimeframeForYesterday()).thenReturn(new TimeframeDTO(1L, 2L));
        when(fddbAdapter.retrieveDataToTimeframe(any())).thenReturn("<html></html>");

        // when
        Health health = fddbHealthIndicator.health();

        // then
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void health_shouldReportDown_whenAuthenticationFails() {
        // given
        when(timeframeCalculator.calculateTimeframeForYesterday()).thenReturn(new TimeframeDTO(1L, 2L));
        when(fddbAdapter.retrieveDataToTimeframe(any())).thenReturn("<html></html>");
        doThrow(new AuthenticationException("nope")).when(fddbParserService).checkAuthentication(any());

        // when
        Health health = fddbHealthIndicator.health();

        // then
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void health_shouldReportDown_whenTheAdapterThrows() {
        // given - a network failure must not escape and fail the whole aggregate health endpoint
        when(timeframeCalculator.calculateTimeframeForYesterday()).thenReturn(new TimeframeDTO(1L, 2L));
        when(fddbAdapter.retrieveDataToTimeframe(any())).thenThrow(new IllegalStateException("connection refused"));

        // when
        Health health = fddbHealthIndicator.health();

        // then
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void health_shouldScrapeOnlyOnce_whenCalledTwiceWithinTheCacheTtl() {
        // given
        when(timeframeCalculator.calculateTimeframeForYesterday()).thenReturn(new TimeframeDTO(1L, 2L));
        when(fddbAdapter.retrieveDataToTimeframe(any())).thenReturn("<html></html>");

        // when
        fddbHealthIndicator.health();
        fddbHealthIndicator.health();

        // then
        verify(fddbAdapter, times(1)).retrieveDataToTimeframe(any());
    }
}
