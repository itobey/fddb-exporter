package dev.itobey.adapter.api.fddb.exporter.adapter;

import dev.itobey.adapter.api.fddb.exporter.config.TelemetryFeignConfig;
import dev.itobey.adapter.api.fddb.exporter.dto.telemetry.TelemetryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "telemetryApi", url = "${fddb-exporter.telemetry.url}", configuration = TelemetryFeignConfig.class)
public interface TelemetryApi {

    @PostMapping("/api/v1/fddb-exporter")
    void sendTelemetryData(@RequestBody TelemetryDto telemetryDto);
}
