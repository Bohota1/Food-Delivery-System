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

    // Order statuses
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    // Payment statuses mirrored onto the order
    public static final String PAYMENT_PENDING = "PENDING";
    public static final String PAYMENT_SUCCESS = "SUCCESS";
}
