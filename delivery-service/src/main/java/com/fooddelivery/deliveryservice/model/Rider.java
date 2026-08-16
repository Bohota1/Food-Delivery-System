package com.fooddelivery.deliveryservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "riders")
public class Rider {

    @Id
    private String id;

    private String name;
    private String phone;
    private String vehicleType; // e.g. BIKE, CAR, BICYCLE

    @Builder.Default
    private boolean available = true;

    private String currentDeliveryId; // null when not on an active delivery

    @CreatedDate
    private LocalDateTime createdAt;
}
