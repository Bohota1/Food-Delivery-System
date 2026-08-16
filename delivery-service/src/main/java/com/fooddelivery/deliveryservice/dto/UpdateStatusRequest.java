package com.fooddelivery.deliveryservice.dto;

import com.fooddelivery.deliveryservice.model.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotNull(message = "status is required")
    private DeliveryStatus status;
}
