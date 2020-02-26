package com.inspiring.surf.integration.broker;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class BrokerMessageLifeCycle implements SmartLifecycle {

    private @Autowired BrokerMessageConfig brokerMessageConfig;
    private boolean running = false;

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        try {
            brokerMessageConfig.stop();
            running = false;
        } finally {
            callback.run();
        }
    }

    @Override
    public void start() {
        brokerMessageConfig.start();
        running = true;
    }

    @Override
    public void stop() {
        brokerMessageConfig.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 10;
    }

}