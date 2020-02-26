package com.inspiring.surf.integration.rest;

import javax.annotation.PostConstruct;

import com.inspiring.surf.integration.listeners.PropertyListener;
import com.inspiring.surf.integration.util.BlockingThreadPoolExecutor;
import com.inspiring.surf.integration.util.DynamicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class IepRestConfig implements PropertyListener {

    private static final String IEP_SERVER_MAX_CONNECTIONS = "iep.server.max.connections";
    private static final Logger report = LoggerFactory.getLogger("monitor.reporter.log");

    private @Autowired DynamicProperties dynamicProperties;

    @PostConstruct
    public void init() {
        dynamicProperties.registerListener(IEP_SERVER_MAX_CONNECTIONS, this);
    }

    @Bean
    public BlockingThreadPoolExecutor iepServerRestExecutor() {
        BlockingThreadPoolExecutor executor = new BlockingThreadPoolExecutor("IepServerRest");
        executor.setMaxPoolSize(getMaxConnections());
        executor.setCorePoolSize(getMaxConnections());
        executor.setQueueCapacity(getMaxConnections());
        return executor;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void threadPoolInfo() {
        report.info("Thread Pool: {}, Busy: {}, Current: {}, Max: {}", iepServerRestExecutor().getThreadPoolName(), iepServerRestExecutor().getActiveCount(),
                iepServerRestExecutor().getPoolSize(), iepServerRestExecutor().getMaxPoolSize());
    }

    private int getMaxConnections() {
        return dynamicProperties.getInteger(IEP_SERVER_MAX_CONNECTIONS, 20);
    }

    @Override
    public void notify(String name, String value) {
        if (IEP_SERVER_MAX_CONNECTIONS.equals(name)) {
            iepServerRestExecutor().setCorePoolSize(getMaxConnections());
            iepServerRestExecutor().setMaxPoolSize(getMaxConnections());
        }
    }
}

