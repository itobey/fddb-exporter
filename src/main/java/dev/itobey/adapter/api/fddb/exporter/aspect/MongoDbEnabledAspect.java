package dev.itobey.adapter.api.fddb.exporter.aspect;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.exception.PersistenceDisabledException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Rejects {@code @RequiresMongoDb} calls when MongoDB is disabled.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MongoDbEnabledAspect {

    static final String MESSAGE = "This operation requires MongoDB to be enabled";

    private final FddbExporterProperties properties;

    /**
     * @throws PersistenceDisabledException if MongoDB is disabled (HTTP 503)
     */
    @Around("@annotation(dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb)")
    public Object checkMongoDbEnabled(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getPersistence().getMongodb().isEnabled()) {
            throw new PersistenceDisabledException(MESSAGE);
        }
        return joinPoint.proceed();
    }
}
