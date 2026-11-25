package com.ibatulanand.orderservice.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourcePropertiesTest {

    private static Properties properties;

    @BeforeAll
    static void loadApplicationProperties() throws IOException {
        properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    }

    @Test
    void credentialsComeFromEnvironmentWithoutDefaults() {
        assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${SPRING_DATASOURCE_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${SPRING_DATASOURCE_PASSWORD}");
    }

    @Test
    void applicationAccountIsNotAskedToCreateOrAlterSchema() {
        assertThat(properties.getProperty("spring.datasource.url")).doesNotContain("createDatabaseIfNotExist");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(new ClassPathResource("data.sql").exists()).isFalse();
    }
}
