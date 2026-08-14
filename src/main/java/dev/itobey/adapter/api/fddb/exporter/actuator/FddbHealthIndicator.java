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
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * This class checks if the login to FDDB is successful and reports this back to Actuator.
 * <p>
 * Every check scrapes a full diary day from fddb.info under the user's own account, so the result is
 * cached for {@link #CACHE_TTL}: the aggregate /actuator/health endpoint may be polled often (a monitoring
 * system, a dashboard load), but the answer only ever changes when the credentials or the site do.
 */
@Component("fddb-login-check")
@RequiredArgsConstructor
@Slf4j
public class FddbHealthIndicator implements HealthIndicator {

    static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final FddbParserService fddbParserService;
    private final FddbAdapter fddbAdapter;
    private final TimeframeCalculator timeframeCalculator;
    private final Clock clock = Clock.systemUTC();

    private volatile Health cachedHealth;
    private volatile Instant cachedAt;

    @Override
    public Health health() {
        Health cached = cachedHealth;
        if (cached != null && Duration.between(cachedAt, clock.instant()).compareTo(CACHE_TTL) < 0) {
            log.debug("returning cached FDDB healthcheck result");
            return cached;
        }
        synchronized (this) {
            cached = cachedHealth;
            if (cached != null && Duration.between(cachedAt, clock.instant()).compareTo(CACHE_TTL) < 0) {
                return cached;
            }
            Health health = performCheck();
            cachedHealth = health;
            cachedAt = clock.instant();
            return health;
        }
    }

    private Health performCheck() {
        log.debug("running healthcheck to check authentication to FDDB");
        try {
            TimeframeDTO timeframeDTO = timeframeCalculator.calculateTimeframeForYesterday();
            String html = fddbAdapter.retrieveDataToTimeframe(timeframeDTO);
            Document doc = Jsoup.parse(html, "UTF-8");
            fddbParserService.checkAuthentication(doc);
            return Health.up().withDetail("FDDB Status", "Authentication seems valid").build();
        } catch (AuthenticationException authenticationException) {
            return Health.down().withDetail("FDDB Status", "Not functioning properly, Authentication seems invalid").build();
        } catch (Exception exception) {
            // never let this escape: it would fail the whole aggregate /actuator/health endpoint and take
            // the MongoDB and InfluxDB status down with it
            log.warn("FDDB healthcheck failed unexpectedly", exception);
            return Health.down()
                    .withDetail("FDDB Status", "Could not be determined: " + exception.getMessage())
                    .build();
        }
    }
}
