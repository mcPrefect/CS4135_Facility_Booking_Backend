package com.facilitybooking.bookingservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NlpEventsConfig {

    @Bean
    public TopicExchange nlpEventsExchange() {
        return new TopicExchange("nlp.events", true, false);
    }

    @Bean
    public Queue nlpBookingIntentQueue() {
        return QueueBuilder.durable("booking.nlp.intent.queue").build();
    }

    @Bean
    public Binding nlpBookingIntentBinding(Queue nlpBookingIntentQueue, TopicExchange nlpEventsExchange) {
        return BindingBuilder.bind(nlpBookingIntentQueue).to(nlpEventsExchange).with("nlp.booking.intent.resolved");
    }
}
