package dev.itobey.adapter.api.fddb.exporter.rest.v2;

import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationInputDto;
import dev.itobey.adapter.api.fddb.exporter.dto.correlation.CorrelationOutputDto;
import dev.itobey.adapter.api.fddb.exporter.service.CorrelationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit test for v2 Correlation API.
 */
@ExtendWith(MockitoExtension.class)
@Tag("v2")
class CorrelationResourceV2Test {

    @Mock
    private CorrelationService correlationService;

    @InjectMocks
    private CorrelationResourceV2 correlationResourceV2;

    @Test
    void testCreateCorrelation() {
        CorrelationInputDto inputDto = new CorrelationInputDto();
        CorrelationOutputDto expectedOutput = new CorrelationOutputDto();

        when(correlationService.createCorrelation(inputDto)).thenReturn(expectedOutput);

        CorrelationOutputDto response = correlationResourceV2.createCorrelation(inputDto);

        assertEquals(expectedOutput, response);
    }
}

