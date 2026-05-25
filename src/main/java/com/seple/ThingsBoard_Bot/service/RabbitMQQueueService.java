package com.seple.ThingsBoard_Bot.service;

import com.seple.ThingsBoard_Bot.config.RabbitMQConfig;
import com.seple.ThingsBoard_Bot.entity.Customer;
import com.seple.ThingsBoard_Bot.repository.CustomerRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQQueueService {

    private final AmqpAdmin amqpAdmin;
    private final CustomerRepository customerRepository;
    private final RabbitListenerEndpointRegistry registry;

    @PostConstruct
    public void initQueues() {
        log.info("[RABBITMQ] Initializing customer-specific queues...");
        try {
            List<Customer> customers = customerRepository.findAll();
            for (Customer customer : customers) {
                declareQueueForCustomer(customer.getCustomerId());
            }
        } catch (Exception e) {
            log.error("[RABBITMQ] Failed to initialize customer queues: {}", e.getMessage(), e);
        }
    }

    public void declareQueueForCustomer(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return;
        }
        String queueName = "iot.events." + customerId;
        String routingKey = "iot.event." + customerId;

        // 1. Declare Durable Queue
        Queue queue = new Queue(queueName, true);
        amqpAdmin.declareQueue(queue);

        // 2. Declare Topic Exchange binding
        TopicExchange exchange = new TopicExchange(RabbitMQConfig.EXCHANGE_NAME);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        amqpAdmin.declareBinding(binding);

        log.info("[RABBITMQ] Declared and bound queue '{}' to exchange '{}' with routing key '{}'",
                queueName, RabbitMQConfig.EXCHANGE_NAME, routingKey);

        // 3. Dynamically add the queue to the listener container
        registerQueueWithListener(queueName);
    }

    private void registerQueueWithListener(String queueName) {
        MessageListenerContainer container = registry.getListenerContainer("eventListener");
        if (container instanceof SimpleMessageListenerContainer) {
            SimpleMessageListenerContainer simpleContainer = (SimpleMessageListenerContainer) container;
            List<String> activeQueues = Arrays.asList(simpleContainer.getQueueNames());
            if (!activeQueues.contains(queueName)) {
                simpleContainer.addQueueNames(queueName);
                log.info("[RABBITMQ] Programmatically registered queue '{}' with listener container", queueName);
            }
        } else {
            log.warn("[RABBITMQ] Listener container 'eventListener' is not active or not an instance of SimpleMessageListenerContainer");
        }
    }
}
