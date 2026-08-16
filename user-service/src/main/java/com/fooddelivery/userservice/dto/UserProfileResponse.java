package com.fooddelivery.userservice.dto;

import com.fooddelivery.userservice.model.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    private List<Address> addresses;
    private LocalDateTime createdAt;
}
