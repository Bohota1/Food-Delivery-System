package com.example.order_service;

public class Constants {
    // Outbound: payment requests consumed by Payment Service.
    public static final String QUEUE = "order_queue";
    public static final String EXCHANGE = "order_exchange";
    public static final String ROUTING_KEY = "order_routingKey";

    // Inbound: payment confirmations published by Payment Service.
    // These three values must match Payment Service's Constants exactly.
    public static final String PAYMENT_QUEUE = "payment_queue";
    public static final String PAYMENT_EXCHANGE = "payment_exchange";
    public static final String PAYMENT_ROUTING_KEY = "payment_routingKey";

    // Outbound: order-confirmed events consumed by Delivery Service.
    // These three values must match Delivery Service's RabbitMQConfig exactly
    // (note the dot-separated style - that service uses a different convention).
    public static final String ORDER_CONFIRMED_QUEUE = "order.confirmed.queue";
    public static final String ORDER_CONFIRMED_EXCHANGE = "order.exchange";
    public static final String ORDER_CONFIRMED_ROUTING_KEY = "order.confirmed";

    // Outbound: order-cancelled events consumed by Delivery Service, so a delivery
    // for a cancelled order is called off and its rider freed.
    public static final String ORDER_CANCELLED_QUEUE = "order.cancelled.queue";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";

    // Inbound: delivery progress published by Delivery Service, which is how the order
    // reaches DELIVERED. Must match Delivery Service's RabbitMQConfig exactly.
    public static final String DELIVERY_EXCHANGE = "delivery.exchange";
    public static final String DELIVERY_STATUS_QUEUE = "delivery.status.queue";
    public static final String DELIVERY_STATUS_ROUTING_KEY = "delivery.status";

    // Outbound: refund requests consumed by Payment Service when a paid order is cancelled.
    public static final String REFUND_QUEUE = "refund_queue";
    public static final String REFUND_EXCHANGE = "refund_exchange";
    public static final String REFUND_ROUTING_KEY = "refund_routingKey";

    // Order statuses
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String DELIVERED = "DELIVERED";

    // Payment statuses mirrored onto the order
    public static final String PAYMENT_PENDING = "PENDING";
    public static final String PAYMENT_SUCCESS = "SUCCESS";
    public static final String PAYMENT_REFUNDED = "REFUNDED";
}
