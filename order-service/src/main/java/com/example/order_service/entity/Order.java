package com.example.order_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    @Id
    private String id;

    @Field
    private String customerId;

    @Field
    private String restaurantId;

    @Field
    private String productId;

    @Field
    private int quantity;

    @Field
    private double price;

    // The customer's cart. `price` above stays the order TOTAL, which is what
    // Payment Service charges, so single-item orders placed the old way still work.
    @Field
    private List<OrderItem> items = new ArrayList<>();

    @Field
    private String restaurantName;

    @Field
    private String status; // PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, CANCELLED, DELIVERED

    @Field
    private String cancelReason;

    @Field
    private String paymentId;

    @Field
    private String paymentStatus; // PENDING, SUCCESS, FAILED

    @Field
    private String deliveryId;

    @Field
    private String deliveryStatus; // PENDING, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED

    @Field
    private String riderId;

    // Needed by Delivery Service when the order is confirmed. Supplied by the
    // customer on the original order request.
    @Field
    private String deliveryAddress;

    @Field
    private String pickupAddress;
}