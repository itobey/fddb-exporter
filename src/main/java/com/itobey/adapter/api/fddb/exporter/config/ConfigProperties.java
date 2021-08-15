package com.itobey.adapter.api.fddb.exporter.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @ConfigurationProperties("fddb")
    public static class Fddb {
        private String cookie;
        private String basicauth;
    }

    /**
     * Postgres properties
     */
    @Getter
    @Setter
    @ConfigurationProperties("postgres")
    public static class Postgres {
        private String server;
        private String user;
        private String password;
        private String database;
    }

}
