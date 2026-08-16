package com.fooddelivery.deliveryservice.dto;

import com.fooddelivery.deliveryservice.model.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResponse {
    private String id;
    private String orderId;
    private String customerId;
    private String restaurantId;
    private String riderId;
    private DeliveryStatus status;
    private String deliveryAddress;
    private String pickupAddress;
    private LocalDateTime assignedAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
