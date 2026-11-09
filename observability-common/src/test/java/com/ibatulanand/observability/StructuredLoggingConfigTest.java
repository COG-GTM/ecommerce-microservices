package com.ibatulanand.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingConfigTest {

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void reset() {
        MDC.clear();
        loggingSystem.cleanUp();
    }

    @Test
    void sharedLogbackConfigEmitsJsonWithStandardFields(CapturedOutput output) throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "sample-service");
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);

        MDC.put(CorrelationIds.MDC_KEY, "corr-42");
        MDC.put("traceId", "trace-7");
        LoggerFactory.getLogger("sample.logger").info("hello {}", "world");

        String line = Arrays.stream(output.getOut().split("\\R"))
                .filter(l -> l.contains("hello world"))
                .findFirst()
                .orElseThrow();
        JsonNode json = new ObjectMapper().readTree(line);
        assertThat(json.get("service").asText()).isEqualTo("sample-service");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("logger").asText()).isEqualTo("sample.logger");
        assertThat(json.get("message").asText()).isEqualTo("hello world");
        assertThat(json.get("correlationId").asText()).isEqualTo("corr-42");
        assertThat(json.get("traceId").asText()).isEqualTo("trace-7");
        assertThat(json.has("timestamp")).isTrue();
    }
}
