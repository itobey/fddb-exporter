package dev.itobey.adapter.api.fddb.exporter.service;

import dev.itobey.adapter.api.fddb.exporter.domain.ExecutionMode;
import dev.itobey.adapter.api.fddb.exporter.service.telemetry.EnvironmentDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentDetectorTest {

    @InjectMocks
    private EnvironmentDetector environmentDetector;

    @Mock
    private Environment environment;

    @Test
    void getExecutionMode_whenExecutionIsKubernetes_shouldReturnKubernetes() {
        when(environment.containsProperty("KUBERNETES_SERVICE_HOST")).thenReturn(true);
        assertEquals(ExecutionMode.KUBERNETES, environmentDetector.getExecutionMode());
    }

    @Test
    void getExecutionMode_whenExecutionIsContainerAndEnvSet_shouldReturnContainer() {
        when(environment.containsProperty("KUBERNETES_SERVICE_HOST")).thenReturn(false);
        when(environment.containsProperty("KUBERNETES_SERVICE_PORT")).thenReturn(false);
        when(environment.containsProperty("DOCKER_CONTAINER")).thenReturn(true);
        assertEquals(ExecutionMode.CONTAINER, environmentDetector.getExecutionMode());
    }

    @Test
    void getExecutionMode_whenExecutionIsContainerAndCgroupMatches_shouldReturnContainer() {
        Path mockPath = Mockito.mock(Path.class);
        try (MockedStatic<Paths> mockedPaths = Mockito.mockStatic(Paths.class);
             MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {

            mockedPaths.when(() -> Paths.get("/proc/1/cgroup")).thenReturn(mockPath);
            mockedFiles.when(() -> Files.readAllBytes(mockPath)).thenReturn("docker".getBytes());

            assertEquals(ExecutionMode.CONTAINER, environmentDetector.getExecutionMode());
        }
    }

    @Test
    void testGetExecutionMode_Jar() {
        assertEquals(ExecutionMode.JAR, environmentDetector.getExecutionMode());
    }
}
