package com.inspiring.surf.integration.server.jersey.customizer.impl;


import java.io.IOException;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;
import org.springframework.core.io.ClassPathResource;

public class SslCustomizer implements JettyServerCustomizer {

    private static final Logger log = LoggerFactory.getLogger(SslCustomizer.class);

    private @Value("${custom.server.ssl.enabled:false}") Boolean sslEnabled;
    private @Value("${custom.server.ssl.port:8443}") Integer sslPort;
    private @Value("${custom.server.ssl.need.client.auth:false}") Boolean sslNeedClientAuth;
    private @Value("${custom.server.ssl.keystore.file:keystore}") String keystoreFile;
    private @Value("${custom.server.ssl.keystore.pass:changeit}") String keystorePass;
    private @Value("${custom.server.ssl.keystore.type:JKS}") String keystoreType;

    @Override
    public void customize(Server server) {
        if (!sslEnabled) {
            log.info("Jetty SSL is disabled");
            return;
        }

        String keystorePath;

        try {
            keystorePath = new ClassPathResource(keystoreFile).getFile().getAbsolutePath();
        } catch (IOException e) {
            throw new FatalBeanException("Cannot load keystore file: " + keystoreFile, e);
        }

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePassword(keystorePass);
        sslContextFactory.setKeyStoreType(keystoreType);
        sslContextFactory.setKeyStorePath(keystorePath);
        sslContextFactory.setTrustStorePassword(keystorePass);
        sslContextFactory.setTrustStoreType(keystoreType);
        sslContextFactory.setTrustStorePath(keystorePath);
        sslContextFactory.setNeedClientAuth(sslNeedClientAuth);

        HttpConfiguration https = new HttpConfiguration();
        https.addCustomizer(new SecureRequestCustomizer());

        ServerConnector sslConnector = new ServerConnector(
                server,
                new SslConnectionFactory(sslContextFactory, "HTTP/1.1"),
                new HttpConnectionFactory(https));
        sslConnector.setPort(sslPort);

        server.addConnector(sslConnector);
    }

}
