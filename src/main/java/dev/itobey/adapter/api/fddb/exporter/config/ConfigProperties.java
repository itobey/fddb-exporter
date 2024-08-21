package dev.itobey.adapter.api.fddb.exporter.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * properties for configuration
 */
@ConfigurationProperties("fddb-exporter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfigProperties {

    private Postgres influxdb;
    private Fddb fddb;

    /**
     * FDDB.info properties
     */
    @Getter
    @Setter
    public static class Fddb {
        private String cookie;
        private String basicauth;
    }

    /**
     * Postgres properties
     */
    @Getter
    @Setter
    public static class Postgres {
        private String server;
        private String user;
        private String password;
        private String database;
    }

}
