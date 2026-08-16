package com.fooddelivery.userservice.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String fullName;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number must be valid")
    private String phone;
}
