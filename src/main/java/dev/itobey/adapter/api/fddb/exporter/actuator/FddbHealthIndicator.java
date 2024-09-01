package dev.itobey.adapter.api.fddb.exporter.actuator;

import dev.itobey.adapter.api.fddb.exporter.adapter.FddbAdapter;
import dev.itobey.adapter.api.fddb.exporter.dto.TimeframeDTO;
import dev.itobey.adapter.api.fddb.exporter.exception.AuthenticationException;
import dev.itobey.adapter.api.fddb.exporter.service.FddbParserService;
import dev.itobey.adapter.api.fddb.exporter.service.TimeframeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * This class checks if the login to FDDB is successful and reports this back to Actuator.
 */
@Component("fddb-login-check")
@RequiredArgsConstructor
@Slf4j
public class FddbHealthIndicator implements HealthIndicator {

    private final FddbParserService fddbParserService;
    private final FddbAdapter fddbAdapter;
    private final TimeframeCalculator timeframeCalculator;

    @Override
    public Health health() {
        log.debug("running healthcheck to check authentication to FDDB");
        TimeframeDTO timeframeDTO = timeframeCalculator.calculateTimeframeForYesterday();
        String html = fddbAdapter.retrieveDataToTimeframe(timeframeDTO);
        Document doc = Jsoup.parse(html, "UTF-8");
        try {
            fddbParserService.checkAuthentication(doc);
            return Health.up().withDetail("FDDB Status", "Authentication seems valid").build();
        } catch (AuthenticationException authenticationException) {
            return Health.down().withDetail("FDDB Status", "Not functioning properly, Authentication seems invalid").build();
        }
    }
}