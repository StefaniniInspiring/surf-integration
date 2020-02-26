package com.inspiring.surf.integration.server.jersey;

import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
@ApplicationPath("/v1")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        packages("com.inspiring");
    }
}
