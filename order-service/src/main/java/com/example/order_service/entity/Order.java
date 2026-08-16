package com.example.order_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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

    @Field
    private String status; // PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, DELIVERED

    @Field
    private String paymentId;

    @Field
    private String paymentStatus; // PENDING, SUCCESS, FAILED
}