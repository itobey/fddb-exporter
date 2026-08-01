package dev.itobey.adapter.api.fddb.exporter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the environment variable names the documentation tells users to set.
 * <p>
 * {@code FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED} is the first property in this project with a hyphen
 * in the <em>leaf</em> segment, and it is the only way to turn the export tools on. If it ever stops
 * binding, the failure mode is that the tools are simply not registered - which is indistinguishable
 * from the feature working exactly as designed, so nobody would notice. Hence a test rather than a
 * one-off check.
 * <p>
 * {@link SystemEnvironmentPropertySource} is the class Spring puts real environment variables into,
 * so binding through it exercises the same relaxed-name path a running container does.
 */
class FddbExporterPropertiesBindingTest {

    @Test
    void writeToolsFlagShouldBindFromTheDocumentedEnvironmentVariable() {
        FddbExporterProperties properties = bind(Map.of(
                "FDDB-EXPORTER_MCP_ENABLED", "true",
                "FDDB-EXPORTER_MCP_WRITE-TOOLS-ENABLED", "true"));

        assertTrue(properties.getMcp().isEnabled());
        assertTrue(properties.getMcp().isWriteToolsEnabled());
    }

    @Test
    void writeToolsFlagShouldAlsoBindFromTheAllUnderscoreForm() {
        // POSIX shells reject a hyphen in an `export`, so this is the form a user reaching for
        // `export` ends up with - it has to work too, and the docs show both
        FddbExporterProperties properties = bind(Map.of(
                "FDDB_EXPORTER_MCP_WRITE_TOOLS_ENABLED", "true"));

        assertTrue(properties.getMcp().isWriteToolsEnabled());
    }

    @Test
    void writeToolsFlagShouldStayOffWhenNothingSetsIt() {
        FddbExporterProperties properties = bind(Map.of("FDDB-EXPORTER_MCP_ENABLED", "true"));

        assertTrue(properties.getMcp().isEnabled());
        assertFalse(properties.getMcp().isWriteToolsEnabled(),
                "a server that was only asked to be enabled must not come up able to write");
    }

    private FddbExporterProperties bind(Map<String, Object> environmentVariables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        environmentVariables));

        return Binder.get(environment)
                .bindOrCreate("fddb-exporter", FddbExporterProperties.class);
    }
}
