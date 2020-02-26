package com.inspiring.surf.integration.server.jersey.customizer;


import com.inspiring.surf.integration.listeners.PropertyListener;
import com.inspiring.surf.integration.server.jersey.customizer.impl.*;
import com.inspiring.surf.integration.util.DynamicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.inspiring.surf.integration.server.jersey.customizer.impl.ThreadPoolCustomizer.CUSTOM_SERVER_THREAD_POOL_IDLE_TIMEOUT;
import static com.inspiring.surf.integration.server.jersey.customizer.impl.ThreadPoolCustomizer.CUSTOM_SERVER_THREAD_POOL_MAX_THREADS;
import static com.inspiring.surf.integration.server.jersey.customizer.impl.ThreadPoolCustomizer.CUSTOM_SERVER_THREAD_POOL_MIN_THREADS;

@Configuration
public class JettyCustomizer implements EmbeddedServletContainerCustomizer, PropertyListener {

    private static final Logger log = LoggerFactory.getLogger(JettyCustomizer.class);
    private @Autowired DynamicProperties dynamicProperties;

    @Override
    public void customize(ConfigurableEmbeddedServletContainer container) {
        if (container instanceof JettyEmbeddedServletContainerFactory) {
            JettyEmbeddedServletContainerFactory factory = (JettyEmbeddedServletContainerFactory) container;

            factory.addServerCustomizers(jmxCustomizer(),
                    sslCustomizer(),
                    threadPoolCustomizer(),
                    formCustomizer(),
                    httpCustomizer());
            dynamicProperties.registerListener(CUSTOM_SERVER_THREAD_POOL_IDLE_TIMEOUT, this);
            dynamicProperties.registerListener(CUSTOM_SERVER_THREAD_POOL_MIN_THREADS, this);
            dynamicProperties.registerListener(CUSTOM_SERVER_THREAD_POOL_MAX_THREADS, this);
        } else {
            log.warn("Jetty container factory not found - Jetty configuration skipped");
        }
    }

    @Bean
    public JmxCustomizer jmxCustomizer() {
        return new JmxCustomizer();
    }

    @Bean
    public SslCustomizer sslCustomizer() {
        return new SslCustomizer();
    }

    @Bean
    public ThreadPoolCustomizer threadPoolCustomizer() {
        return new ThreadPoolCustomizer(dynamicProperties);
    }

    @Bean
    public FormCustomizer formCustomizer() {
        return new FormCustomizer();
    }

    @Bean
    public HttpCustomizer httpCustomizer() {
        return new HttpCustomizer();
    }

    @Override
    public void notify(String name, String value) {
        threadPoolCustomizer().reloadThreadPoolConfiguration();
    }

}

