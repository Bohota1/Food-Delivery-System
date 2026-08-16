package com.example.payment_service.config;

import com.example.payment_service.Constants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Payment Service sits in the middle of the RabbitMQ chain, so it declares two topologies:
 * the order topology it consumes from, and the payment topology it publishes to.
 * Both sides declaring the same queue/exchange/binding is safe - declarations are idempotent.
 */
@Configuration
public class MessagingConfig {

    // ----- Inbound: Order Service -> Payment Service -----

    @Bean
    public Queue orderQueue() {
        return new Queue(Constants.ORDER_QUEUE);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(Constants.ORDER_EXCHANGE);
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(Constants.ORDER_ROUTING_KEY);
    }

    // ----- Outbound: Payment Service -> Order Service -----

    @Bean
    public Queue paymentQueue() {
        return new Queue(Constants.PAYMENT_QUEUE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(Constants.PAYMENT_EXCHANGE);
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentQueue).to(paymentExchange).with(Constants.PAYMENT_ROUTING_KEY);
    }

    // ----- Inbound: Order Service -> Payment Service (refund requests) -----

    @Bean
    public Queue refundQueue() {
        return new Queue(Constants.REFUND_QUEUE, true);
    }

    @Bean
    public TopicExchange refundExchange() {
        return new TopicExchange(Constants.REFUND_EXCHANGE);
    }

    @Bean
    public Binding refundBinding(Queue refundQueue, TopicExchange refundExchange) {
        return BindingBuilder.bind(refundQueue).to(refundExchange).with(Constants.REFUND_ROUTING_KEY);
    }

    // ----- Shared -----

    @Bean
    public MessageConverter converter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Declared as RabbitTemplate (not AmqpTemplate) so it can be injected by either type.
     * Spring Boot also applies this single MessageConverter bean to the @RabbitListener
     * container factory, which is what lets the listener receive a Map instead of raw bytes.
     */
    @Bean
    public RabbitTemplate template(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        return rabbitTemplate;
    }
}
