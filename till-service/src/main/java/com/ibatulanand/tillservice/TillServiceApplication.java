package com.ibatulanand.tillservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TillServiceApplication.class, args);
    }
}
