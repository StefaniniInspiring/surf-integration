package com.inspiring.surf.integration.server.jersey.customizer.impl;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;

public class HttpCustomizer implements JettyServerCustomizer {

    private static final Logger log = LoggerFactory.getLogger(HttpCustomizer.class);

    private @Value("${custom.server.request.max.header.bytes:8192}") Integer maxHeaderBytes;

    @Override
    public void customize(Server server) {
        HttpConfiguration httpConfiguration = server.getBean(ServerConnector.class)
                .getConnectionFactory(HttpConnectionFactory.class)
                .getHttpConfiguration();

        if (httpConfiguration == null) {
            log.warn("Jetty Http Configuration not found");
            return;
        }

        httpConfiguration.setRequestHeaderSize(maxHeaderBytes);
    }

}
