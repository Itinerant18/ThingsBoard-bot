package com.seple.ThingsBoard_Bot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "iot.exchange";
    public static final String QUEUE_NAME = "iot.events";
    public static final String ROUTING_KEY = "iot.event.#";
    public static final String DLX_NAME = "iot.dlx";
    public static final String DLQ_NAME = "iot.events.dlq";

    @Bean
    public TopicExchange iotExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    /**
     * Main work queue, dead-lettered to {@link #DLX_NAME}. NOTE: adding the dead-letter argument
     * to an already-declared queue triggers a PRECONDITION_FAILED on RabbitMQ — an existing
     * {@code iot.events} queue must be deleted/recreated (or migrated) for this to take effect.
     */
    @Bean
    public Queue iotEventsQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .build();
    }

    @Bean
    public Binding iotEventsBinding(Queue iotEventsQueue, TopicExchange iotExchange) {
        return BindingBuilder.bind(iotEventsQueue).to(iotExchange).with(ROUTING_KEY);
    }

    @Bean
    public TopicExchange iotDeadLetterExchange() {
        return new TopicExchange(DLX_NAME);
    }

    /** Parking queue for messages that exhaust retries, so failures are investigable, not lost. */
    @Bean
    public Queue iotDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding iotDeadLetterBinding(Queue iotDeadLetterQueue, TopicExchange iotDeadLetterExchange) {
        return BindingBuilder.bind(iotDeadLetterQueue).to(iotDeadLetterExchange).with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setExchange(EXCHANGE_NAME);
        template.setRoutingKey("iot.event.device");
        return template;
    }
}