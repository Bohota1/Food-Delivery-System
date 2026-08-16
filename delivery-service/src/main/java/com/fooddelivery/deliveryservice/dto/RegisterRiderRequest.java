package com.fooddelivery.deliveryservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRiderRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "phone must be valid")
    private String phone;

    @NotBlank(message = "vehicleType is required")
    private String vehicleType;
}
