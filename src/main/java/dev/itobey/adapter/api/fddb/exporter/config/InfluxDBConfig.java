package dev.itobey.adapter.api.fddb.exporter.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class InfluxDBConfig {

    private final FddbExporterProperties properties;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(properties.getInfluxdb().getUrl(),
                properties.getInfluxdb().getToken().toCharArray(),
                properties.getInfluxdb().getOrg(),
                properties.getInfluxdb().getBucket());
    }
}
