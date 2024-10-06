package dev.itobey.adapter.api.fddb.exporter.service.telemetry;

import dev.itobey.adapter.api.fddb.exporter.domain.ExecutionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * This class is used to detect the execution mode of the application for telemetry purposes.
 */
@Component
@RequiredArgsConstructor
public class EnvironmentDetector {

    private final Environment environment;

    public ExecutionMode getExecutionMode() {
        if (isRunningInKubernetes()) {
            return ExecutionMode.KUBERNETES;
        } else if (isRunningInPlainContainer()) {
            return ExecutionMode.CONTAINER;
        } else {
            return ExecutionMode.JAR;
        }
    }

    private boolean isRunningInKubernetes() {
        return environment.containsProperty("KUBERNETES_SERVICE_HOST") ||
                environment.containsProperty("KUBERNETES_SERVICE_PORT");
    }

    private boolean isRunningInPlainContainer() {
        // Check for Docker-specific environment variable
        if (environment.containsProperty("DOCKER_CONTAINER")) {
            return true;
        }

        // Check for the presence of .dockerenv file
        if (new File("/.dockerenv").exists()) {
            return true;
        }

        // Check cgroup
        try {
            String cgroup = new String(Files.readAllBytes(Paths.get("/proc/1/cgroup")));
            return cgroup.contains("docker") || cgroup.contains("containerd");
        } catch (Exception e) {
            // File doesn't exist or can't be read, probably not in a container
        }

        return false;
    }
}
