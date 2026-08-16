package com.example.payment_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "payments")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Payment {
    @Id
    private String id;

    @Field
    private String orderId;

    @Field
    private String customerId;

    @Field
    private double amount;

    @Field
    private String method; // SIMULATED_GATEWAY, CARD, COD

    @Field
    private String status; // PENDING, SUCCESS, FAILED

    @Field
    private String transactionId;

    @Field
    private String failureReason;

    @Field
    private String refundReason;

    private Instant refundedAt;

    private Instant createdAt;

    @Field
    private Instant processedAt;
}
