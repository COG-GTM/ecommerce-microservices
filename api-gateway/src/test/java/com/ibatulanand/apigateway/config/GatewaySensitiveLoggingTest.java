package com.ibatulanand.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GatewaySensitiveLoggingTest {

    private static final String GATEWAY_LOGGER = "org.springframework.cloud.gateway";

    private static final List<String> HEADER_LOGGING_LOGGERS = List.of(
            "org.springframework.cloud.gateway.filter.headers.observation.ObservedRequestHttpHeadersFilter",
            "org.springframework.cloud.gateway.filter.headers.observation.ObservedResponseHttpHeadersFilter");

    private static final List<String> VERBOSE_LEVELS = List.of("DEBUG", "TRACE", "ALL");

    private static final Pattern COMPOSE_LOGGING_ENTRY = Pattern.compile(
            "^-?\\s*[\"']?(LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_(?:CLOUD_GATEWAY|SECURITY)\\w*)\\s*[=:]\\s*[\"']?(\\w*)");

    @Test
    void headerObservationFiltersDoNotLogAtDebugOrTrace() throws IOException {
        Properties properties = applicationProperties();

        for (String logger : HEADER_LOGGING_LOGGERS) {
            assertThat(effectiveLevel(properties, logger))
                    .as("effective log level of %s", logger)
                    .isNotIn(VERBOSE_LEVELS);
        }
    }

    @Test
    void headerObservationFiltersStayQuietWhenGatewayLoggingIsRaised() throws IOException {
        Properties properties = applicationProperties();

        for (String verboseLevel : VERBOSE_LEVELS) {
            properties.setProperty("logging.level." + GATEWAY_LOGGER, verboseLevel);
            for (String logger : HEADER_LOGGING_LOGGERS) {
                assertThat(effectiveLevel(properties, logger))
                        .as("effective log level of %s with %s=%s", logger, GATEWAY_LOGGER, verboseLevel)
                        .isNotIn(VERBOSE_LEVELS);
            }
        }
    }

    @Test
    void composeDoesNotRaiseGatewayOrSecurityLoggingToDebugOrTrace() throws IOException {
        List<String> verboseLoggingOverrides = composeLoggingEntries().stream()
                .filter(entry -> VERBOSE_LEVELS.contains(entry.level()))
                .map(ComposeLoggingEntry::line)
                .toList();

        assertThat(verboseLoggingOverrides).isEmpty();
    }

    @Test
    void composePinsHeaderObservationLoggingForPrebuiltGatewayImages() throws IOException {
        assertThat(composeLoggingEntries())
                .filteredOn(entry -> entry.key().equals("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_GATEWAY_FILTER_HEADERS_OBSERVATION"))
                .extracting(ComposeLoggingEntry::level)
                .as("compose must override the TRACE gateway logging baked into older api-gateway images")
                .isNotEmpty()
                .allMatch(level -> !level.isEmpty() && !VERBOSE_LEVELS.contains(level));
    }

    private static List<ComposeLoggingEntry> composeLoggingEntries() throws IOException {
        Optional<Path> compose = Stream.of(Path.of("docker-compose.yml"), Path.of("..", "docker-compose.yml"))
                .filter(path -> Files.isDirectory(path.toAbsolutePath().getParent().resolve("api-gateway")))
                .filter(Files::exists)
                .findFirst();
        assumeTrue(compose.isPresent(), "repository docker-compose.yml not found next to api-gateway/");

        return Files.readAllLines(compose.get()).stream()
                .map(String::trim)
                .map(line -> {
                    Matcher matcher = COMPOSE_LOGGING_ENTRY.matcher(line);
                    return matcher.find()
                            ? new ComposeLoggingEntry(line, matcher.group(1), matcher.group(2).toUpperCase(Locale.ROOT))
                            : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private record ComposeLoggingEntry(String line, String key, String level) {
    }

    private static Properties applicationProperties() throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
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
