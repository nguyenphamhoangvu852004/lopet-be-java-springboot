package com.nguyenvu.lopet;

import com.nguyenvu.lopet.config.RabbitMQConfig;
import com.rabbitmq.client.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

public class Consumer {
    public static void main(String[] args) throws NoSuchAlgorithmException, KeyManagementException, IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv("RABBIT_MQ_HOST"));
        factory.setPort(Integer.parseInt(System.getenv("RABBIT_MQ_PORT")));
        factory.setUsername(System.getenv("RABBIT_MQ_USERNAME"));
        factory.setPassword(System.getenv("RABBIT_MQ_PASSWORD"));
        factory.setVirtualHost(System.getenv("RABBIT_MQ_VHOST"));
        factory.useSslProtocol();

        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(factory);
        Connection connection = factory.newConnection("lopet:consumer");
        Channel c = connection.createChannel();

        boolean ack = false;

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println("Received: " + body);
            c.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        };

        CancelCallback cancelCallback = consumerTag ->
                System.out.println("Consumer cancelled: " + consumerTag);

        c.basicConsume(RabbitMQConfig.QUEUE_NAME,deliverCallback,cancelCallback);
        System.out.println("Listening on queue '" + RabbitMQConfig.QUEUE_NAME + "'... press Enter to stop");
        System.in.read(); // keep main() from returning immediately
        c.close();
        connection.close();

    }
}
