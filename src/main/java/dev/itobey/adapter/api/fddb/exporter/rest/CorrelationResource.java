package dev.itobey.adapter.api.fddb.exporter.rest;

import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/correlation")
@Slf4j
@Validated
@RequiredArgsConstructor
public class CorrelationResource {

    private final CorrelationService correlationService;

    @PostMapping
    public CorrelationOutputDto createCorrelation(@RequestBody CorrelationInputDto correlationInputDto) {
        return correlationService.createCorrelation(correlationInputDto);
    }

}
