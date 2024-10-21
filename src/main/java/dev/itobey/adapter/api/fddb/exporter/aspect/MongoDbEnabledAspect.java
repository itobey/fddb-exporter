package dev.itobey.adapter.api.fddb.exporter.aspect;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class MongoDbEnabledAspect {

    private final FddbExporterProperties properties;

    @Around("@annotation(dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb)")
    public Object checkMongoDbEnabled(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getPersistence().getMongodb().isEnabled()) {
            return ResponseEntity.badRequest().body("This operation requires MongoDB to be enabled");
        }
        return joinPoint.proceed();
    }
}
