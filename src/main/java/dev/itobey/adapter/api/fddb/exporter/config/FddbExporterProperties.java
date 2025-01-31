package dev.itobey.adapter.api.fddb.exporter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fddb-exporter")
@Data
public class FddbExporterProperties {

    private Fddb fddb;
    private Scheduler scheduler;
    private Telemetry telemetry;
    private Persistence persistence;
    private Influxdb influxdb;
    private Notification notification;

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

}
