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
public class InfluxDbEnabledAspect {

    private final FddbExporterProperties properties;

    @Around("@annotation(dev.itobey.adapter.api.fddb.exporter.annotation.RequiresInfluxDb)")
    public Object checkInfluxDbEnabled(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.getPersistence().getInfluxdb().isEnabled()) {
            return ResponseEntity.badRequest().body("This operation requires InfluxDB to be enabled");
        }
        return joinPoint.proceed();
    }
}
