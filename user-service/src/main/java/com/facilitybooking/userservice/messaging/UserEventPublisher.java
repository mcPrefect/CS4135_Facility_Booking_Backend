package com.facilitybooking.userservice.messaging;

import com.facilitybooking.userservice.domain.entity.User;
import com.facilitybooking.userservice.mapper.UserEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UserEventPublisher {

    private static  final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UserEventMapper userEventMapper;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing.user-regitered}")
    private String routingKey;

    public void publishUserRegistered(User user) {
        UserRegisteredEvent userRegisteredEvent = userEventMapper.toEvent(user);

        int maxRetries = 3;
        int attempts = 0;

        while (attempts < maxRetries) {
            try {

                rabbitTemplate.convertAndSend(exchange, routingKey, userRegisteredEvent);
                return;
            } catch (Exception e) {
                attempts++;
                log.warn("Attempt {} failed to sent event", attempts, e);

                try {
                    Thread.sleep(1000);

                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        // fallback after all retries fail
        log.error("Failed to send user registered event, continuing without messaging");
    }
}
