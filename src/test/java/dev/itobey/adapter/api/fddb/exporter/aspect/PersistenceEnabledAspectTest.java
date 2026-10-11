package dev.itobey.adapter.api.fddb.exporter.aspect;

import dev.itobey.adapter.api.fddb.exporter.config.FddbExporterProperties;
import dev.itobey.adapter.api.fddb.exporter.exception.PersistenceDisabledException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Verifies disabled stores throw a 503-mapped exception without running the guarded endpoint.
 */
@ExtendWith(MockitoExtension.class)
class PersistenceEnabledAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private FddbExporterProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FddbExporterProperties();
        FddbExporterProperties.Persistence persistence = new FddbExporterProperties.Persistence();
        persistence.setMongodb(new FddbExporterProperties.Persistence.MongoDB());
        persistence.setInfluxdb(new FddbExporterProperties.Persistence.Influxdb());
        properties.setPersistence(persistence);
    }

    @Nested
    class MongoDb {

        @Test
        void whenEnabled_shouldProceedAndPassTheResultThrough() throws Throwable {
            properties.getPersistence().getMongodb().setEnabled(true);
            when(joinPoint.proceed()).thenReturn("the real result");

            Object result = new MongoDbEnabledAspect(properties).checkMongoDbEnabled(joinPoint);

            assertThat(result).isEqualTo("the real result");
        }

        @Test
        void whenDisabled_shouldRefuseWithoutRunningTheEndpoint() throws Throwable {
            properties.getPersistence().getMongodb().setEnabled(false);
            MongoDbEnabledAspect aspect = new MongoDbEnabledAspect(properties);

            assertThatThrownBy(() -> aspect.checkMongoDbEnabled(joinPoint))
                    // IllegalStateException maps to HTTP 503.
                    .isInstanceOf(PersistenceDisabledException.class)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(MongoDbEnabledAspect.MESSAGE);
            verify(joinPoint, never()).proceed();
        }
    }

    @Nested
    class InfluxDb {

        @Test
        void whenEnabled_shouldProceedAndPassTheResultThrough() throws Throwable {
            properties.getPersistence().getInfluxdb().setEnabled(true);
            when(joinPoint.proceed()).thenReturn("the real result");

            Object result = new InfluxDbEnabledAspect(properties).checkInfluxDbEnabled(joinPoint);

            assertThat(result).isEqualTo("the real result");
        }

        @Test
        void whenDisabled_shouldRefuseWithoutRunningTheEndpoint() throws Throwable {
            properties.getPersistence().getInfluxdb().setEnabled(false);
            InfluxDbEnabledAspect aspect = new InfluxDbEnabledAspect(properties);

            assertThatThrownBy(() -> aspect.checkInfluxDbEnabled(joinPoint))
                    .isInstanceOf(PersistenceDisabledException.class)
                    .hasMessage(InfluxDbEnabledAspect.MESSAGE);
            verify(joinPoint, never()).proceed();
        }

        /**
         * Each store must be enabled independently.
         */
        @Test
        void whenOnlyMongoDbIsEnabled_shouldStillRefuse() throws Throwable {
            properties.getPersistence().getMongodb().setEnabled(true);
            properties.getPersistence().getInfluxdb().setEnabled(false);
            InfluxDbEnabledAspect aspect = new InfluxDbEnabledAspect(properties);

            assertThatThrownBy(() -> aspect.checkInfluxDbEnabled(joinPoint))
                    .isInstanceOf(PersistenceDisabledException.class);
            verify(joinPoint, never()).proceed();
        }
    }
}
