package com.inspiring.surf.integration.broker;

public class BrokerRetryException extends RuntimeException{

    public BrokerRetryException(String msg) {
        super(msg);
    }

}
