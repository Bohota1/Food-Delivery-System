package com.example.order_service.config;

import com.example.order_service.Constants;
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

@Configuration
public class MessagingConfig {

    // ----- Outbound: Order Service -> Payment Service -----

    @Bean
    public Queue queue() {
        return new Queue(Constants.QUEUE);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(Constants.EXCHANGE);
    }

    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(Constants.ROUTING_KEY);
    }

    // ----- Inbound: Payment Service -> Order Service -----

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

    // ----- Outbound: Order Service -> Delivery Service -----
    // Delivery Service declares these same three too. Declaring them here as well
    // means the queue exists even if Delivery Service has not started yet, so
    // confirmed-order events are buffered instead of dropped.

    @Bean
    public Queue orderConfirmedQueue() {
        return new Queue(Constants.ORDER_CONFIRMED_QUEUE, true);
    }

    @Bean
    public TopicExchange orderConfirmedExchange() {
        return new TopicExchange(Constants.ORDER_CONFIRMED_EXCHANGE);
    }

    @Bean
    public Binding orderConfirmedBinding(Queue orderConfirmedQueue, TopicExchange orderConfirmedExchange) {
        return BindingBuilder.bind(orderConfirmedQueue).to(orderConfirmedExchange)
                .with(Constants.ORDER_CONFIRMED_ROUTING_KEY);
    }

    // ----- Outbound: Order Service -> Delivery Service (cancellations) -----

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(Constants.ORDER_CANCELLED_QUEUE, true);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange orderConfirmedExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(orderConfirmedExchange)
                .with(Constants.ORDER_CANCELLED_ROUTING_KEY);
    }

    // ----- Outbound: Order Service -> Payment Service (refunds) -----

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

    @Bean
    public RabbitTemplate template(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter());
        return rabbitTemplate;
    }
}