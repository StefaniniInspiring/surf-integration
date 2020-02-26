package com.inspiring.surf.integration.util;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class BlockingThreadPoolExecutor extends ThreadPoolTaskExecutor {

    private static final long serialVersionUID = 8978238438211367401L;

    private String threadPoolName;

    public BlockingThreadPoolExecutor(String threadPoolName) {
        super();
        this.threadPoolName = threadPoolName;
    }

    @Override
    protected BlockingQueue<Runnable> createQueue(int queueCapacity) {
        if (queueCapacity > 0) {
            return new LinkedBlockingQueue<Runnable>(queueCapacity) {

                private static final long serialVersionUID = -26551340448190778L;

                @Override
                public boolean offer(Runnable runnable) {
                    try {
                        super.put(runnable);
                    } catch (InterruptedException e) {
                        return false;
                    }

                    return true;
                }

                ;
            };
        }

        return super.createQueue(queueCapacity);
    }

    public String getThreadPoolName() {
        return threadPoolName;
    }
}