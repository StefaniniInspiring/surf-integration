package com.inspiring.surf.integration.rest;

import com.google.common.io.Resources;
import com.inspiring.surf.integration.broker.BrokerMessageConfig;
import com.inspiring.surf.integration.broker.BrokerRetryException;
import com.inspiring.surf.integration.listeners.PropertyListener;
import com.inspiring.surf.integration.util.BlockingThreadPoolExecutor;
import com.inspiring.surf.integration.util.DynamicProperties;
import com.inspiring.surf.integration.util.MapUtils;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class IepRestClient implements PropertyListener {

    private static final Logger log = LoggerFactory.getLogger(IepRestClient.class);
    private static final Logger error = LoggerFactory.getLogger("integration.audit.error");
    private static final Logger success = LoggerFactory.getLogger("integration.audit.success");

    private static final String HEADER_RESPONSE_ERROR_CODE = "X-SMKT-ErrorCode";
    private static final String HEADER_RESPONSE_ERROR_MSG = "X-SMKT-ErrorMessage";
    private static final String IEP_SERVER_ENABLE = "iep.server.enable";
    private static final String IEP_SERVER_URL = "iep.server.url";
    private static final String IEP_SERVER_REQUEST_TIMEOUT = "iep.server.request.timeout";
    private static final String IEP_SERVER_CONNECTION_TIMEOUT = "iep.server.connection.timeout";
    private static final String IEP_SERVER_SOCKET_TIMEOUT = "iep.server.socket.timeout";
    private static final String IEP_SERVER_MAX_CONNECTIONS = "iep.server.max.connections";
    private static final String IEP_EVENT_ENDPOINT = "/rs/event";
    private static final String IEP_SERVER_TEMPLATE_RELOAD_INTERVAL = "iep.server.template.reload.interval.seconds";

    private @Autowired DynamicProperties dynamicProperties;
    private @Autowired ResourceLoader resourceLoader;
    private @Autowired BrokerMessageConfig broker;
    private @Autowired BlockingThreadPoolExecutor iepServerRestExecutor;

    private RestTemplate iepServer;
    private Map<String, Map.Entry<Long, String>> eventTemplates = new HashMap<>();

    @PostConstruct
    public void init() {
        dynamicProperties.registerListener(IEP_SERVER_REQUEST_TIMEOUT, this);
        dynamicProperties.registerListener(IEP_SERVER_CONNECTION_TIMEOUT, this);
        dynamicProperties.registerListener(IEP_SERVER_SOCKET_TIMEOUT, this);
        dynamicProperties.registerListener(IEP_SERVER_MAX_CONNECTIONS, this);
        dynamicProperties.registerListener(IEP_SERVER_ENABLE, this);
        updateHttpClientConfig();
    }

    public void executeEvent(String source, String eventTemplateName, Map<String, Object> variables) throws Throwable {

        if (!isIepServerEnable()) {
            log.debug("Iep Server Disabled");
            throw new BrokerRetryException("Iep Server Disabled");
        }

        try {
            runAsync(() -> {

                String body = createEvent(eventTemplateName, variables);

                MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
                headers.add("Content-Type", "application/json");

                HttpEntity<String> request = new HttpEntity<>(body, headers);
                try {
                    log.debug("Executing event: {}", body);
                    ResponseEntity<Object> responseEntity = iepServer.postForEntity(getIepServerUrl() + IEP_EVENT_ENDPOINT, request, Object.class);

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        log.debug("Event executed with success: {}", body);
                        success.info("type={}|{}", source, MapUtils.toString(variables, "|"));
                    } else {
                        error.info("error=HTTP-{}|type={}|{}", responseEntity.getStatusCode().value(), source, MapUtils.toString(variables, "|"));
                    }
                } catch (HttpStatusCodeException e) {
                    if (e.getStatusCode().is4xxClientError()) {
                        String errorCode = e.getResponseHeaders().getFirst(HEADER_RESPONSE_ERROR_CODE);
                        String errorMsg = e.getResponseHeaders().getFirst(HEADER_RESPONSE_ERROR_MSG);
                        error.info("error={}|type={}|{}", isBlank(errorCode) ? e.getStatusCode() : errorCode, source, MapUtils.toString(variables, "|"));
                        log.warn("Event executed with error: {} - {}", errorCode, errorMsg);
                    } else {
                        log.error("Event not executed, server error:  {}", e.getStatusCode());
                        error.info("error={}|type={}|{}", e.getStatusCode(), source, MapUtils.toString(variables, "|"));
                    }
                } catch (RestClientException e) {
                    if (e.getRootCause() instanceof SocketTimeoutException) {
                        throw new BrokerRetryException("SocketTimeoutException");
                    } else if (e.getRootCause() instanceof ConnectTimeoutException) {
                        throw new BrokerRetryException("ConnectTimeoutException");
                    } else if (e.getRootCause() instanceof ConnectException) {
                        throw new BrokerRetryException("ConnectException");
                    }
                    if (log.isDebugEnabled()) {
                        log.error("Event not executed, server error.", e);
                    } else {
                        log.error("Event not executed, server error: {}", e.getMessage());
                    }
                    error.info("error=0|type={}|{}", source, MapUtils.toString(variables, "|"));
                }
            }, iepServerRestExecutor).get();
        } catch (InterruptedException e) {
            if (log.isDebugEnabled()) {
                log.error("Event interrupted.", e);
            } else {
                log.error("Event interrupted: {}", e.getMessage());
            }
            error.info("error=0|type={}|{}", source, MapUtils.toString(variables, "|"));
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    private String createEvent(String templateName, Map<String, Object> variables) {
        String template = getEventRequestTemplate(templateName);
        StrSubstitutor sub = new StrSubstitutor(variables);
        return sub.replace(template);
    }

    private String getEventRequestTemplate(String templateName) {
        if (eventTemplates.containsKey(templateName)) {
            Map.Entry<Long, String> template = eventTemplates.get(templateName);
            if (template.getKey() > currentTimeMillis() + getFileReloadInterval()) {
                return loadTemplate(templateName);
            }
            return template.getValue();
        } else {
            return loadTemplate(templateName);
        }
    }

    private String loadTemplate(String templateName) {
        Resource file = resourceLoader.getResource("classpath:" + templateName);
        if (file.exists()) {
            try {
                long lastModified = file.getFile().lastModified();
                if (eventTemplates.containsKey(templateName)) {
                    if (eventTemplates.get(templateName).getKey() == lastModified) {
                        return eventTemplates.get(templateName).getValue();
                    }
                }
                String template = Resources.toString(file.getURL(), UTF_8);
                Map.Entry<Long, String> entry = new AbstractMap.SimpleEntry<>(lastModified, template);
                eventTemplates.put(templateName, entry);
                return template;
            } catch (IOException e) {
                log.error("Error loading event template: " + templateName, e);
                throw new RuntimeException("Error loading event template: " + templateName, e);
            }
        }
        log.error("Error loading event template: {}, file not found", templateName);
        throw new RuntimeException("Error loading event template, file not found: " + templateName);
    }

    private CloseableHttpClient createHttpClient() {

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(getSocketTimeout()).build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(getRequestTimeout())
                .setConnectTimeout(getConnectionTimeout())
                .setSocketTimeout(getSocketTimeout())
                .setCookieSpec("default").build();

        return HttpClients.custom()
                .setConnectionManager(createConnectionManager())
                .setDefaultSocketConfig(socketConfig)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(10, TimeUnit.SECONDS)
                .build();
    }

    private HttpClientConnectionManager createConnectionManager() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.closeIdleConnections(20, TimeUnit.SECONDS);
        cm.setMaxTotal(getMaxConnections());
        cm.setDefaultMaxPerRoute(getMaxConnections());
        cm.setValidateAfterInactivity(30000);
        return cm;
    }

    private String getIepServerUrl() {
        return dynamicProperties.getString(IEP_SERVER_URL);
    }

    private long getFileReloadInterval() {
        return dynamicProperties.getLong(IEP_SERVER_TEMPLATE_RELOAD_INTERVAL, 30) * 1000;
    }

    private boolean isIepServerEnable() {
        return dynamicProperties.getBoolean(IEP_SERVER_ENABLE, true);
    }

    private int getSocketTimeout() {
        return dynamicProperties.getInteger(IEP_SERVER_SOCKET_TIMEOUT, 30000);
    }

    private int getConnectionTimeout() {
        return dynamicProperties.getInteger(IEP_SERVER_CONNECTION_TIMEOUT, 30000);
    }

    private int getRequestTimeout() {
        return dynamicProperties.getInteger(IEP_SERVER_REQUEST_TIMEOUT, 30000);
    }

    private int getMaxConnections() {
        return dynamicProperties.getInteger(IEP_SERVER_MAX_CONNECTIONS, 20);
    }

    @Override
    public void notify(String name, String value) {

        if (IEP_SERVER_CONNECTION_TIMEOUT.equals(name)
                || IEP_SERVER_REQUEST_TIMEOUT.equals(name)
                || IEP_SERVER_SOCKET_TIMEOUT.equals(name)
                || IEP_SERVER_MAX_CONNECTIONS.equals(name)) {

            updateHttpClientConfig();
        } else if (IEP_SERVER_ENABLE.equals(name)) {
            if (isIepServerEnable()) {
                broker.start();
            } else {
                broker.stop();
            }
        }
    }

    private void updateHttpClientConfig() {
        RestTemplate iepServerTmp = new RestTemplate(new HttpComponentsClientHttpRequestFactory(createHttpClient()));
        iepServerTmp.getMessageConverters().add(0, new StringHttpMessageConverter(Charset.forName("UTF-8")));
        iepServer = iepServerTmp;
    }
}
