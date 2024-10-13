package dev.itobey.adapter.api.fddb.exporter.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MongoDbEnabledAspect {

    @Value("${fddb-exporter.persistence.mongodb.enabled}")
    private boolean mongoDbEnabled;

    @Around("@annotation(dev.itobey.adapter.api.fddb.exporter.annotation.RequiresMongoDb)")
    public Object checkMongoDbEnabled(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!mongoDbEnabled) {
            return ResponseEntity.badRequest().body("This operation requires MongoDB to be enabled");
        }
        return joinPoint.proceed();
    }
}
