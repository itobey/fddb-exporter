package dev.itobey.adapter.api.fddb.exporter.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDBConfig {

    @Value("${fddb-exporter.influxdb.url}")
    private String influxDbUrl;

    @Value("${fddb-exporter.influxdb.token}")
    private String influxDbToken;

    @Value("${fddb-exporter.influxdb.org}")
    private String influxDbOrg;

    @Value("${fddb-exporter.influxdb.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(influxDbUrl, influxDbToken.toCharArray(), influxDbOrg, bucket);
    }
}
