package com.fooddelivery.deliveryservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDeliveryRequest {

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotBlank(message = "customerId is required")
    private String customerId;

    @NotBlank(message = "restaurantId is required")
    private String restaurantId;

    @NotBlank(message = "deliveryAddress is required")
    private String deliveryAddress;

    private String pickupAddress;
}
