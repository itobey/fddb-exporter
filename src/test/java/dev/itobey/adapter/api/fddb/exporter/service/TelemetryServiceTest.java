package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.adapter.TelemetryApi;
import dev.itobey.adapter.api.fddb.exporter.domain.ExecutionMode;
import dev.itobey.adapter.api.fddb.exporter.dto.telemetry.TelemetryDto;
import dev.itobey.adapter.api.fddb.exporter.service.telemetry.EnvironmentDetector;
import dev.itobey.adapter.api.fddb.exporter.service.telemetry.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @InjectMocks
    private TelemetryService telemetryService;

    @Mock
    private TelemetryApi telemetryApi;
    @Mock
    private PersistenceService persistenceService;
    @Mock
    private EnvironmentDetector environmentDetector;
    @Mock
    private BuildProperties buildProperties;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(telemetryService, "fddbUserMail", "test@example.com");
    }

    @Test
    void sendTelemetryData_shouldSendTelemetryData() {
        // given
        when(persistenceService.countAllEntries()).thenReturn(10L);
        when(environmentDetector.getExecutionMode()).thenReturn(ExecutionMode.CONTAINER);
        when(buildProperties.getVersion()).thenReturn("1.0.0");

        // when
        telemetryService.sendTelemetryData();

        // then
        ArgumentCaptor<TelemetryDto> telemetryDtoCaptor = ArgumentCaptor.forClass(TelemetryDto.class);
        verify(telemetryApi, times(1)).sendTelemetryData(telemetryDtoCaptor.capture());

        TelemetryDto capturedDto = telemetryDtoCaptor.getValue();
        assertNotNull(capturedDto);
        assertEquals(10L, capturedDto.getDocumentCount());
        assertEquals("973dfe463ec85785f5f95af5ba3906eedb2d931c24e69824a89ea65dba4e813b", capturedDto.getMailHash());
    }

}