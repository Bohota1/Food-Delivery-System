package com.fooddelivery.deliveryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Shape of the message the Order Service publishes to RabbitMQ once an
 * order is confirmed. The Delivery Service listens for this and creates
 * a Delivery record automatically, per the proposal's async workflow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedEvent implements Serializable {
    private String orderId;
    private String customerId;
    private String restaurantId;
    private String deliveryAddress;
    private String pickupAddress;
}
