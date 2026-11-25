package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySensitiveLoggingTest {

    private static final List<String> HEADER_LOGGING_LOGGERS = List.of(
            "org.springframework.cloud.gateway.filter.headers.observation.ObservedRequestHttpHeadersFilter",
            "org.springframework.cloud.gateway.filter.headers.observation.ObservedResponseHttpHeadersFilter");

    private static final List<String> VERBOSE_LEVELS = List.of("DEBUG", "TRACE", "ALL");

    @Test
    void headerObservationFiltersDoNotLogAtDebugOrTrace() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        for (String logger : HEADER_LOGGING_LOGGERS) {
            assertThat(effectiveLevel(properties, logger))
                    .as("effective log level of %s", logger)
                    .isNotIn(VERBOSE_LEVELS);
        }
    }

    @Test
    void composeDoesNotRaiseGatewayOrSecurityLoggingToDebugOrTrace() throws IOException {
        Path compose = Path.of("..", "docker-compose.yml");
        if (!Files.exists(compose)) {
            return;
        }

        List<String> verboseLoggingOverrides = Files.readAllLines(compose).stream()
                .map(String::trim)
                .filter(line -> line.matches("-?\\s*LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_(CLOUD_GATEWAY|SECURITY)\\w*\\s*[=:].*"))
                .filter(line -> VERBOSE_LEVELS.stream().anyMatch(level -> line.toUpperCase(Locale.ROOT).endsWith(level)))
                .toList();

        assertThat(verboseLoggingOverrides).isEmpty();
    }

    private static String effectiveLevel(Properties properties, String logger) {
        String name = logger;
        while (true) {
            String level = properties.getProperty("logging.level." + name);
            if (level != null) {
                return level.trim().toUpperCase(Locale.ROOT);
            }
            int dot = name.lastIndexOf('.');
            if (dot < 0) {
                return properties.getProperty("logging.level.root", "INFO").trim().toUpperCase(Locale.ROOT);
            }
            name = name.substring(0, dot);
        }
    }
}
