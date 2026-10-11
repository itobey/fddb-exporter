package dev.itobey.adapter.api.fddb.exporter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "fddb-exporter")
@Data
public class FddbExporterProperties {

    /**
     * Shared timezone for scrape windows, export dates and InfluxDB timestamps, independent of JVM defaults.
     */
    private String timezone = "Europe/Berlin";

    private Fddb fddb;
    private Scheduler scheduler;
    private Telemetry telemetry;
    private VersionCheck versionCheck;
    private Persistence persistence;
    private Influxdb influxdb;
    private Notification notification;
    private Ui ui;
    private Mcp mcp;

    /**
     * Resolves {@link #timezone}; avoids a getter so the binder does not treat it as a nested property.
     *
     * @return the configured zone
     * @throws java.time.DateTimeException for an invalid zone ID
     */
    public ZoneId resolveZone() {
        return ZoneId.of(timezone);
    }

    @Data
    public static class Fddb {
        private String url;
        private String username;
        private String password;
        private int minDaysBack;
        private int maxDaysBack;
    }

    @Data
    public static class Scheduler {
        private boolean enabled;
        private String cron;
    }

    @Data
    public static class Telemetry {
        private String url;
        private String username;
        private String token;
        private String cron;
    }

    /**
     * The scheduled check for a new release. It shares nothing with telemetry but used to run on the
     * telemetry cron, so a deployment that retimed telemetry retimed this too.
     */
    @Data
    public static class VersionCheck {
        private String cron;
    }

    @Data
    public static class Persistence {
        private MongoDB mongodb;
        private Influxdb influxdb;

        @Data
        public static class MongoDB {
            private boolean enabled;
        }

        @Data
        public static class Influxdb {
            private boolean enabled;
        }
    }

    @Data
    public static class Influxdb {
        private String url;
        private String token;
        private String org;
        private String bucket;
    }

    @Data
    public static class Notification {
        private Telegram telegram;
        private boolean enabled;

        @Data
        public static class Telegram {
            private String token;
            private String chatId;
        }
    }

    @Data
    public static class Ui {
        private String fddbLinkPrefix;
    }

    /**
     * The MCP server exposes the exported nutrition data to MCP clients. It is disabled by default,
     * since it serves personal health data over an unauthenticated endpoint.
     */
    @Data
    public static class Mcp {
        private boolean enabled;

        /**
         * Whether the export tools are registered on top of the read-only ones. They scrape
         * fddb.info under the configured account and write to the database, so a read-only server
         * is the default. Evaluated at startup: changing it needs a restart, and while it is off
         * the tools are not registered at all rather than refusing at call time.
         */
        private boolean writeToolsEnabled;
    }

}
