package com.inspiring.surf.integration.server;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

import static org.springframework.context.annotation.FilterType.REGEX;

@SpringBootApplication
@ComponentScan(basePackages = "com.inspiring.surf.integration", excludeFilters = @ComponentScan.Filter(pattern = "com.inspiring.surf.integration.*.*.unit.*", type = REGEX))
public class IntegrationServer extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationServer.class, args);
    }
}
