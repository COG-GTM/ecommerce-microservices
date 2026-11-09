package com.ibatulanand.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private static final AutoConfigurations CONFIG = AutoConfigurations.of(ObservabilityAutoConfiguration.class);

    @Test
    void registersServletFilterFirstInServletApps() {
        new WebApplicationContextRunner().withConfiguration(CONFIG).run(context -> {
            FilterRegistrationBean<?> registration =
                    context.getBean("correlationIdFilterRegistration", FilterRegistrationBean.class);
            assertThat(registration.getFilter()).isInstanceOf(CorrelationIdFilter.class);
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
            assertThat(context).doesNotHaveBean(ReactiveCorrelationIdFilter.class);
        });
    }

    @Test
    void registersWebFilterInReactiveApps() {
        new ReactiveWebApplicationContextRunner().withConfiguration(CONFIG).run(context -> {
            assertThat(context).hasSingleBean(ReactiveCorrelationIdFilter.class);
            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
    }

    @Test
    void registersNothingOutsideWebApps() {
        new ApplicationContextRunner().withConfiguration(CONFIG).run(context -> {
            assertThat(context).doesNotHaveBean(ReactiveCorrelationIdFilter.class);
            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
    }
}
