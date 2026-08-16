package com.fooddelivery.userservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded (not a separate collection) — addresses live inside the User
 * document since they are always accessed together with the owning user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    private String id;          // UUID, generated on creation
    private String label;       // e.g. "Home", "Work"
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private Double latitude;
    private Double longitude;
    private boolean isDefault;
}
