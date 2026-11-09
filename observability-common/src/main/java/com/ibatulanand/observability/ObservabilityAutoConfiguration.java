package com.ibatulanand.observability;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Hooks;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(OncePerRequestFilter.class)
    static class ServletCorrelationIdConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
        FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
            FilterRegistrationBean<CorrelationIdFilter> registration =
                    new FilterRegistrationBean<>(new CorrelationIdFilter());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFilter.class)
    static class ReactiveCorrelationIdConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ReactiveCorrelationIdFilter reactiveCorrelationIdFilter() {
            return new ReactiveCorrelationIdFilter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass({ContextRegistry.class, Hooks.class})
    static class ReactorMdcPropagationConfiguration {

        ReactorMdcPropagationConfiguration() {
            registerMdcAccessor();
        }

        static void registerMdcAccessor() {
            ContextRegistry.getInstance().registerThreadLocalAccessor(
                    CorrelationIds.MDC_KEY,
                    () -> MDC.get(CorrelationIds.MDC_KEY),
                    value -> MDC.put(CorrelationIds.MDC_KEY, value),
                    () -> MDC.remove(CorrelationIds.MDC_KEY));
            Hooks.enableAutomaticContextPropagation();
        }
    }
}
