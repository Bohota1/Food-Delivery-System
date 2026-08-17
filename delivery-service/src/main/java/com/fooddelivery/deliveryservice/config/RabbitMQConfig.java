package com.fooddelivery.deliveryservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_CONFIRMED_QUEUE = "order.confirmed.queue";
    public static final String ORDER_CONFIRMED_ROUTING_KEY = "order.confirmed";
    public static final String ORDER_CANCELLED_QUEUE = "order.cancelled.queue";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";

    // Outbound: delivery progress published back to Order Service, so the order
    // reaches DELIVERED without anyone calling Order Service directly.
    public static final String DELIVERY_EXCHANGE = "delivery.exchange";
    public static final String DELIVERY_STATUS_QUEUE = "delivery.status.queue";
    public static final String DELIVERY_STATUS_ROUTING_KEY = "delivery.status";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderConfirmedQueue() {
        return new Queue(ORDER_CONFIRMED_QUEUE, true);
    }

    @Bean
    public Binding orderConfirmedBinding(Queue orderConfirmedQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderConfirmedQueue).to(orderExchange).with(ORDER_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(ORDER_CANCELLED_QUEUE, true);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(orderExchange).with(ORDER_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public TopicExchange deliveryExchange() {
        return new TopicExchange(DELIVERY_EXCHANGE);
    }

    @Bean
    public Queue deliveryStatusQueue() {
        return new Queue(DELIVERY_STATUS_QUEUE, true);
    }

    @Bean
    public Binding deliveryStatusBinding(Queue deliveryStatusQueue, TopicExchange deliveryExchange) {
        return BindingBuilder.bind(deliveryStatusQueue).to(deliveryExchange).with(DELIVERY_STATUS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
