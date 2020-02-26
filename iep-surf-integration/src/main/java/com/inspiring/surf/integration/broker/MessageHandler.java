package com.inspiring.surf.integration.broker;

import java.util.Map;

public interface MessageHandler {

    void handleMessage(Map<String, Object> request);

}
