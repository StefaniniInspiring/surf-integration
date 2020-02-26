package com.inspiring.surf.integration.server.jersey.customizer.impl;

import com.inspiring.surf.integration.util.DynamicProperties;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;

public class ThreadPoolCustomizer implements JettyServerCustomizer {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolCustomizer.class);

    public static final String CUSTOM_SERVER_THREAD_POOL_MIN_THREADS = "custom.server.thread.pool.min.threads";
    public static final String CUSTOM_SERVER_THREAD_POOL_MAX_THREADS = "custom.server.thread.pool.max.threads";
    public static final String CUSTOM_SERVER_THREAD_POOL_IDLE_TIMEOUT = "custom.server.thread.pool.idle.timeout";
    private Server server;
    private DynamicProperties dynamicProperties;

    public ThreadPoolCustomizer(DynamicProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public void customize(Server server) {
        this.server = server;
        QueuedThreadPool threadPool = server.getBean(QueuedThreadPool.class);

        if (threadPool == null) {
            log.warn("Jetty Thread Pool not found");
            return;
        }

        threadPool.setName("http-server");
        threadPool.setMinThreads(getMinThreads());
        threadPool.setMaxThreads(getMaxThreads());
        threadPool.setIdleTimeout(getIdleTimeout());
    }

    public void reloadThreadPoolConfiguration() {
        QueuedThreadPool threadPool = server.getBean(QueuedThreadPool.class);

        if (threadPool == null) {
            log.warn("Jetty Thread Pool not found");
            return;
        }
        threadPool.setMinThreads(getMinThreads());
        threadPool.setMaxThreads(getMaxThreads());
        threadPool.setIdleTimeout(getIdleTimeout());
    }

    private Integer getMinThreads() {
        return dynamicProperties.getInteger(CUSTOM_SERVER_THREAD_POOL_MIN_THREADS, 8);
    }

    private Integer getMaxThreads() {
        return dynamicProperties.getInteger(CUSTOM_SERVER_THREAD_POOL_MAX_THREADS, 200);
    }

    private Integer getIdleTimeout() {
        return dynamicProperties.getInteger(CUSTOM_SERVER_THREAD_POOL_IDLE_TIMEOUT, 60000);
    }
}
