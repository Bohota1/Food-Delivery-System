package com.fooddelivery.deliveryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiderResponse {
    private String id;
    private String name;
    private String phone;
    private String vehicleType;
    private boolean available;
    private String currentDeliveryId;
}
