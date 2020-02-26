package com.inspiring.surf.integration.broker;

import com.inspiring.surf.integration.exceptions.SendToAuditException;
import com.inspiring.surf.integration.listeners.PropertyListener;
import com.inspiring.surf.integration.util.DynamicProperties;
import com.rabbitmq.http.client.Client;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.removeStart;

@Configuration
@EnableScheduling
public class BrokerMessageConfig implements PropertyListener {

    private static final Logger log = LoggerFactory.getLogger(BrokerMessageConfig.class);
    private static final Logger report = LoggerFactory.getLogger("monitor.reporter.log");
    private static final String QUEUE_RETRY_SUFFIX = ".retry";
    private static final String QUEUE_ERROR_SUFFIX = ".error";
    private static final String MESSAGE_RETRIES_COUNT = "x-msg-retry-count";

    private @Value("${rabbit.hosts:localhost:5672}") String amqpHosts;
    private @Value("${rabbit.client.enabled:true}") boolean rabbitClientEnabled;
    private @Value("${rabbit.client.url:localhost:15672}") String clientUrl;
    private @Value("${rabbit.user:guest}") String amqpUser;
    private @Value("${rabbit.pass:guest}") String amqpPass;
    private @Value("${rabbit.vhost:/}") String amqpVhost;
    private static final String RABBIT_ENABLE = "rabbit.enabled";
    private static final String RABBIT_RETRY_DELAY = "rabbit.retry.delay";
    private static final String RABBIT_RETRY_MAX = "rabbit.retry.max";
    private Client rabbitClient;

    private final AtomicInteger counter = new AtomicInteger(0);

    private @Autowired ConfigurableApplicationContext ctx;
    private @Autowired DynamicProperties dynamicProperties;
    private @Autowired(required = false) AmqpAdmin amqpAdmin;
    private Map<String, RabbitTemplate> templates = new HashMap<>();
    private Map<String, SimpleMessageListenerContainer> containers = new HashMap<>();
    private Map<String, MessageHandler> handlers = new HashMap<>();

    @PostConstruct
    public void init() throws MalformedURLException, URISyntaxException {
        dynamicProperties.registerListener("queue.*", this);
        dynamicProperties.registerListener(RABBIT_ENABLE, this);

        if (rabbitClientEnabled) {
            rabbitClient = new Client(clientUrl, amqpUser, amqpPass);
        }
    }

    public void start() {
        if (isRabbitEnabled()) {
            containers.entrySet().forEach(entry -> {
                if (!entry.getValue().isRunning()) {
                    log.info("Starting consumer for queue: {}", entry.getKey());
                    entry.getValue().start();
                }
            });
        }
    }

    public void stop() {
        if (isRabbitEnabled()) {
            containers.entrySet().forEach(entry -> {
                if (entry.getValue().isRunning()) {
                    log.info("Stopping consumer for queue: {}", entry.getKey());
                    entry.getValue().stop();
                }
            });
        }
    }

    public void registerQueue(String queueName, MessageHandler handler) {
        log.debug("Registering queue: {}", queueName);
        listenerContainer(rabbitTemplate(queueName), handler);
        log.info("Queue {} registered", queueName);
    }

    public void sendMessageToRetry(String queue, Map<String, Object> body) throws SendToAuditException {

        if (isRabbitEnabled()) {
            if (body.containsKey(MESSAGE_RETRIES_COUNT)) {
                int count = (Integer) body.get(MESSAGE_RETRIES_COUNT);
                if (count >= getRetryMax()) {

                    log.error("Message reach retry limit, sending to error queue", body);

                    getTemplate(queue + QUEUE_ERROR_SUFFIX).convertAndSend(body, message -> {
                        body.remove(MESSAGE_RETRIES_COUNT);
                        return message;
                    });
                    return;
                }
                body.put(MESSAGE_RETRIES_COUNT, ++count);
            } else {
                body.put(MESSAGE_RETRIES_COUNT, 1);
            }

            String retryQueue = queue + QUEUE_RETRY_SUFFIX;
            log.debug("Queueing message: '{}' in queue: '{}'", body, retryQueue);

            getTemplate(retryQueue).convertAndSend(body, message -> {
                message.getMessageProperties().setExpiration(String.valueOf(getRetryDelay()));
                return message;
            });
        } else {
            throw new SendToAuditException("Can not send message to retry, broker is disable");
        }
    }

    public void sendMessage(String queue, Map<String, Object> message) {
        if (isRabbitEnabled()) {
            log.debug("Queueing message: '{}' in queue: '{}'", message, queue);
            getTemplate(queue).convertAndSend(message);
        } else {
            log.debug("Sending message direct to handler: {}", message);
            getHandler(queue).handleMessage(message);
        }
    }

    private String rabbitTemplate(String queueName) {

        //Work Queue
        Queue workQueue = workQueue(queueName);
        amqpAdmin.declareQueue(workQueue);
        register(workQueue.getName(), workQueue);

        Binding workBinding = workBinding(workQueue);
        amqpAdmin.declareBinding(workBinding);
        register(workQueue.getName(), workBinding);

        RabbitTemplate workTemplate = new RabbitTemplate(connectionFactory());
        workTemplate.setExchange(workExchange().getName());
        workTemplate.setRoutingKey(workQueue.getName());
        workTemplate.setChannelTransacted(true);
        register(workQueue.getName(), workTemplate);
        templates.put(workQueue.getName(), workTemplate);

        //Retry Queue
        Queue retryQueue = retryQueue(queueName);
        amqpAdmin.declareQueue(retryQueue);
        register(retryQueue.getName(), retryQueue);

        Binding retryBinding = retryBinding(retryQueue);
        amqpAdmin.declareBinding(retryBinding);
        register(retryQueue.getName(), retryBinding);

        RabbitTemplate retryTemplate = new RabbitTemplate(connectionFactory());
        retryTemplate.setExchange(retryExchange().getName());
        retryTemplate.setRoutingKey(retryQueue.getName());
        retryTemplate.setChannelTransacted(true);
        register(retryQueue.getName(), retryTemplate);
        templates.put(retryQueue.getName(), retryTemplate);

        //Error Queue
        Queue errorQueue = errorQueue(queueName);
        amqpAdmin.declareQueue(errorQueue);
        register(errorQueue.getName(), errorQueue);

        Binding errorBinding = errorBinding(errorQueue);
        amqpAdmin.declareBinding(errorBinding);
        register(errorQueue.getName(), errorBinding);

        RabbitTemplate errorTemplate = new RabbitTemplate(connectionFactory());
        errorTemplate.setExchange(errorExchange().getName());
        errorTemplate.setRoutingKey(errorQueue.getName());
        errorTemplate.setChannelTransacted(true);
        register(errorQueue.getName(), errorTemplate);
        templates.put(errorQueue.getName(), errorTemplate);

        return workQueue.getName();
    }

    private SimpleMessageListenerContainer listenerContainer(String queueName, MessageHandler handler) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory());
        container.setQueueNames(queueName);
        container.setMessageListener(new MessageListenerAdapter(handler));
        container.setAutoStartup(false);
        container.setShutdownTimeout(50);
        container.setMaxConcurrentConsumers(getQueueMaxConsumers(queueName));
        container.setConcurrentConsumers(getQueueConsumers(queueName));
        container.setTaskExecutor(inboundRequestExecutor());
        register(queueName, container);
        containers.put(queueName, container);
        handlers.put(queueName, handler);
        return container;
    }

    private void register(String queueName, Object bean) {
        String beanName = queueName + bean.getClass().getSimpleName() + counter.incrementAndGet();
        ctx.getBeanFactory().registerSingleton(beanName, bean);
        ctx.getBeanFactory().initializeBean(bean, beanName);
    }

    @Bean
    public AsyncTaskExecutor inboundRequestExecutor() {
        SimpleAsyncTaskExecutor simpleAsyncTaskExecutor = new SimpleAsyncTaskExecutor("Request Consumer");
        simpleAsyncTaskExecutor.setThreadNamePrefix("request-message-in-");
        return simpleAsyncTaskExecutor;
    }

    @Bean
    public CachingConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setAddresses(amqpHosts);
        connectionFactory.setUsername(amqpUser);
        connectionFactory.setPassword(amqpPass);
        connectionFactory.setVirtualHost(amqpVhost);
        return connectionFactory;
    }

    private Queue workQueue(String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    private Queue retryQueue(String queueName) {
        return QueueBuilder.durable(queueName + QUEUE_RETRY_SUFFIX)
                .withArgument("x-dead-letter-exchange", workExchange().getName())
                .withArgument("x-dead-letter-routing-key", queueName)
                .build();
    }

    private Queue errorQueue(String queueName) {
        return QueueBuilder.durable(queueName + QUEUE_ERROR_SUFFIX).build();
    }

    private Binding workBinding(Queue aQueue) {
        return BindingBuilder.bind(aQueue).to(workExchange()).withQueueName();
    }

    private Binding retryBinding(Queue aQueue) {
        return BindingBuilder.bind(aQueue).to(retryExchange()).withQueueName();
    }

    private Binding errorBinding(Queue aQueue) {
        return BindingBuilder.bind(aQueue).to(errorExchange()).withQueueName();
    }

    @Bean
    public DirectExchange workExchange() {
        return new DirectExchange("x.work.request", true, false);
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange("x.retry.request", true, false);
    }

    @Bean
    public DirectExchange errorExchange() {
        return new DirectExchange("x.error.request", true, false);
    }

    private int getQueueConsumers(String queueName) {
        return dynamicProperties.getInteger("queue." + queueName + ".consumers", 10);
    }

    private int getQueueMaxConsumers(String queueName) {
        return dynamicProperties.getInteger("queue." + queueName + ".consumers.max", 20);
    }

    @Override
    public void notify(String name, String value) {
        if (RABBIT_ENABLE.equals(name)) {
            if (isRabbitEnabled()) {
                start();
            } else {
                stop();
            }
        } else if (name.startsWith("queue.")) {
            String queueName = getQueueName(name);
            if (containers.containsKey(queueName)) {
                if (name.endsWith(".consumers.max")) {
                    containers.get(queueName).setMaxConcurrentConsumers(getQueueMaxConsumers(queueName));
                } else {
                    containers.get(queueName).setConcurrentConsumers(getQueueConsumers(queueName));
                }
            }
        }
    }

    private String getQueueName(String name) {
        if (isNotBlank(name)) {
            String queueName = removeStart(name, "queue.");
            queueName = removeEnd(queueName, ".consumers");
            queueName = removeEnd(queueName, ".consumers.max");
            return queueName;
        }
        return null;
    }

    private MessageHandler getHandler(String queue) {
        if (handlers.containsKey(queue)) {
            return handlers.get(queue);
        }
        throw new RuntimeException("Handler not found: " + queue);
    }

    private RabbitTemplate getTemplate(String queue) {
        if (templates.containsKey(queue)) {
            return templates.get(queue);
        }
        throw new RuntimeException("Queue not found: " + queue);
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void queueInfo() {
        if (rabbitClientEnabled && isRabbitEnabled()) {
            for (String queueName : templates.keySet()) {
                long consumerCount = rabbitClient.getQueue(amqpVhost, queueName).getConsumerCount();
                long totalMessages = rabbitClient.getQueue(amqpVhost, queueName).getTotalMessages();
                long consumerUtilisation = rabbitClient.getQueue(amqpVhost, queueName).getConsumerUtilisation();
                report.info("Queue '{}': Consumers: {}/{}, Total Messages: {}",
                        queueName, consumerUtilisation, consumerCount, totalMessages);
            }
        }
    }

    private Integer getRetryDelay() {
        return dynamicProperties.getInteger(RABBIT_RETRY_DELAY, 30000);
    }

    private Integer getRetryMax() {
        return dynamicProperties.getInteger(RABBIT_RETRY_MAX, 10);
    }

    private boolean isRabbitEnabled() {
        return dynamicProperties.getBoolean(RABBIT_ENABLE, true);
    }
}
