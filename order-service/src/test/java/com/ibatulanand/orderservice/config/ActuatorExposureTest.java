package com.ibatulanand.orderservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.env.EnvironmentEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.logging.LoggersEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.management.HeapDumpWebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.management.ThreadDumpEndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpointWebExtension;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.env.EnvironmentEndpoint;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
import org.springframework.boot.actuate.management.HeapDumpWebEndpoint;
import org.springframework.boot.actuate.management.ThreadDumpEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorExposureTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.DEBUG))
            .withConfiguration(AutoConfigurations.of(
                    EndpointAutoConfiguration.class,
                    WebEndpointAutoConfiguration.class,
                    HealthContributorAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class,
                    HeapDumpWebEndpointAutoConfiguration.class,
                    EnvironmentEndpointAutoConfiguration.class,
                    LoggersEndpointAutoConfiguration.class,
                    ThreadDumpEndpointAutoConfiguration.class));

    @Test
    void onlyHealthIsExposedOverHttpAmongConfiguredEndpoints() {
        contextRunner.run(context -> {
            Set<String> exposed = context.getBean(WebEndpointsSupplier.class).getEndpoints().stream()
                    .map(ExposableWebEndpoint::getEndpointId)
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            assertThat(exposed).containsExactly("health");
            assertThat(context).doesNotHaveBean(HeapDumpWebEndpoint.class);
            assertThat(context).doesNotHaveBean(EnvironmentEndpoint.class);
            assertThat(context).doesNotHaveBean(LoggersEndpoint.class);
            assertThat(context).doesNotHaveBean(ThreadDumpEndpoint.class);
        });
    }

    @Test
    void healthDetailsAreHiddenFromUnauthenticatedCallers() {
        contextRunner.withBean("db", HealthIndicator.class,
                        () -> () -> Health.up().withDetail("url", "jdbc:mysql://order-db").build())
                .run(context -> {
                    WebEndpointResponse<HealthComponent> response = context.getBean(HealthEndpointWebExtension.class)
                            .health(ApiVersion.LATEST, WebServerNamespace.SERVER, SecurityContext.NONE);
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(response.getBody()).isInstanceOf(CompositeHealth.class);
                    assertThat(((CompositeHealth) response.getBody()).getComponents()).isNull();
                });
    }

    @Test
    void datasourceCredentialsAreNotHardcoded() {
        contextRunner.withPropertyValues("SPRING_DATASOURCE_USERNAME=order-user", "SPRING_DATASOURCE_PASSWORD=from-env")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("order-user");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("from-env");
                });
    }
}
