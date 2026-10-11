package dev.itobey.adapter.api.fddb.exporter.aspect;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.exception.PersistenceDisabledException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Rejects {@code @RequiresInfluxDb} calls when InfluxDB is disabled.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class InfluxDbEnabledAspect {

    static final String MESSAGE = "This operation requires InfluxDB to be enabled";

    private final FddbExporterProperties properties;

    /**
     * @throws PersistenceDisabledException if InfluxDB is disabled (HTTP 503)
     */
    @Around("@annotation(dev.itobey.adapter.api.fddb.exporter.annotation.RequiresInfluxDb)")
    public Object checkInfluxDbEnabled(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getPersistence().getInfluxdb().isEnabled()) {
            throw new PersistenceDisabledException(MESSAGE);
        }
        return joinPoint.proceed();
    }
}
