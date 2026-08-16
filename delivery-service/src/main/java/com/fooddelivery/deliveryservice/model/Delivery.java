package com.fooddelivery.deliveryservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "deliveries")
public class Delivery {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;      // reference to Order Service's order

    private String customerId;   // reference to User Service's customer
    private String restaurantId; // reference to Restaurant Service

    private String riderId;      // null until a rider is assigned

    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    private String deliveryAddress;
    private String pickupAddress;

    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
