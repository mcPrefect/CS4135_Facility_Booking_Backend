package com.facilitybooking.userservice.messaging;

import org.springframework.amqp.core.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange("user.events");
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue("user.registered", true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, DirectExchange userExchange) {
        return BindingBuilder
                .bind(userRegisteredQueue)
                .to(userExchange)
                .with("user.registered");
    }

    @Bean
    public ApplicationRunner declareRabbitTopology(
            AmqpAdmin amqpAdmin,
            DirectExchange userExchange,
            Queue userRegisteredQueue,
            Binding userRegisteredBinding
    ) {
        return args -> {
            System.out.println("Declaring RabbitMQ topology...");
            amqpAdmin.declareExchange(userExchange);
            amqpAdmin.declareQueue(userRegisteredQueue);
            amqpAdmin.declareBinding(userRegisteredBinding);
            System.out.println("RabbitMQ topology declared.");
        };
    }
}
