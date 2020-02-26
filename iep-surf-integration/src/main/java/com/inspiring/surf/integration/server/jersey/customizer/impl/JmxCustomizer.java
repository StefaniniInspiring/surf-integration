package com.inspiring.surf.integration.server.jersey.customizer.impl;

import java.lang.management.ManagementFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;

public class JmxCustomizer implements JettyServerCustomizer {

    private static final Logger log = LoggerFactory.getLogger(JmxCustomizer.class);

    private @Value("${custom.server.jmx.enabled:false}") Boolean jmxEnabled;

    @Override
    public void customize(Server server) {
        if (jmxEnabled) {
            server.addBean(new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));
        } else {
            log.info("Jetty JMX is disabled");
        }
    }

}
