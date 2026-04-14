package com.facilitybooking.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchanges.booking-events:booking.events}")
    private String exchange;

    @Value("${rabbitmq.queues.booking-events:notification.booking.events.queue}")
    private String queue;

    @Value("${rabbitmq.queues.dlq:booking.events.dlq}")
    private String dlqName;

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue bookingEventsQueue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", dlqName)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding bookingEventsBinding(Queue bookingEventsQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(bookingEventsQueue).to(bookingEventsExchange).with("booking.#");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory cf) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(messageConverter());
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return org.springframework.retry.interceptor.RetryInterceptorBuilder.stateless()
                .retryOperations(RetryTemplate.builder()
                        .maxAttempts(3)
                        .exponentialBackoff(1000, 2.0, 4000)
                        .build())
                .build();
    }
}
