package com.inspiring.surf.integration.server.jersey.customizer.impl;

import org.eclipse.jetty.server.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;

public class FormCustomizer implements JettyServerCustomizer {

    private static final String JETTY_MAX_FORM_CONTENT_SIZE = "org.eclipse.jetty.server.Request.maxFormContentSize";
    private static final String JETTY_MAX_FORM_KEYS = "org.eclipse.jetty.server.Request.maxFormKeys";

    private @Value("${custom.server.form.max.content.bytes:200000}") Integer maxContentBytes;
    private @Value("${custom.server.form.max.keys:1000}") Integer maxKeys;

    @Override
    public void customize(Server server) {
        server.setAttribute(JETTY_MAX_FORM_CONTENT_SIZE, maxContentBytes);
        server.setAttribute(JETTY_MAX_FORM_KEYS, maxKeys);
    }

}
