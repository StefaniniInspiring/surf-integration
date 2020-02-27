package com.inspiring.surf.integration.service.sms;

import com.inspiring.surf.integration.broker.BrokerMessageConfig;
import com.inspiring.surf.integration.broker.BrokerRetryException;
import com.inspiring.surf.integration.broker.MessageHandler;
import com.inspiring.surf.integration.exceptions.SendToAuditException;
import com.inspiring.surf.integration.rest.IepRestClient;
import com.inspiring.surf.integration.util.DynamicProperties;
import com.inspiring.surf.integration.util.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static com.inspiring.surf.integration.service.sms.SmsInputServiceRest.queueNameStatus;

@Component
public class SmsStatusListener  implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsResponseListener.class);
    private static final Logger reprocess = LoggerFactory.getLogger("integration.audit.reprocess");
    private static final Logger error = LoggerFactory.getLogger("integration.audit.error");

    private static final String TEMPLATE_EVENT_SMS_STATUS = "template.event.sms.status";
    private @Autowired
    IepRestClient iepRestClient;
    private @Autowired
    DynamicProperties dynamicProperties;
    private @Autowired
    BrokerMessageConfig broker;

    @PostConstruct
    public void init() {
        broker.registerQueue(queueNameStatus, this);
    }

    @Override
    public void handleMessage(Map<String, Object> request) {
        log.debug("Message received: {}", request);
        try {
            iepRestClient.executeEvent(queueNameStatus, getEventName(), request);
        } catch (BrokerRetryException e) {
            log.debug("Sending message to retry: {}", request);
            try {
                broker.sendMessageToRetry(queueNameStatus, request);
            } catch (SendToAuditException ea) {
                log.warn(ea.getMessage());
                reprocess.info("type={}|{}", queueNameStatus, MapUtils.toString(request, "|"));
            }
        } catch (Throwable e) {

            error.info("error=0|type={}|{}", queueNameStatus, MapUtils.toString(request, "|"));

            if (log.isDebugEnabled()) {
                log.error("Error processing message: " + request, e);
            } else {
                log.error("Error processing message: {}, cause: {}", request, e.getMessage());
            }
        }
    }

    private String getEventName() {
        return dynamicProperties.getString(TEMPLATE_EVENT_SMS_STATUS);
    }
}

