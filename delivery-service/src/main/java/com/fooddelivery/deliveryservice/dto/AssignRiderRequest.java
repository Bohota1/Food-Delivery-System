package com.fooddelivery.deliveryservice.dto;

import lombok.Data;

@Data
public class AssignRiderRequest {

    /**
     * Optional. If omitted, the service auto-assigns the first available rider.
     */
    private String riderId;
}
