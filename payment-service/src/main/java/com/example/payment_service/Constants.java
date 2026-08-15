package com.example.payment_service;

public class Constants {
    // Inbound: payment requests published by Order Service.
    // These three values must match Order Service's Constants exactly.
    public static final String ORDER_QUEUE = "order_queue";
    public static final String ORDER_EXCHANGE = "order_exchange";
    public static final String ORDER_ROUTING_KEY = "order_routingKey";

    // Outbound: payment confirmations consumed by Order Service.
    public static final String PAYMENT_QUEUE = "payment_queue";
    public static final String PAYMENT_EXCHANGE = "payment_exchange";
    public static final String PAYMENT_ROUTING_KEY = "payment_routingKey";

    // Payment statuses
    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
}
