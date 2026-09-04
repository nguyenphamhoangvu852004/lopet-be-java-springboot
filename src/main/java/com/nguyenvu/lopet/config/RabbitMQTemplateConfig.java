package com.nguyenvu.lopet.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class RabbitMQTemplateConfig {
    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.warn("Broker did not confirm message: {}", cause);
            }
        });

        template.setReturnsCallback(returned ->
                log.warn("Message returned — undeliverable: exchange={}, routingKey={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));

        return template;
    }
}
