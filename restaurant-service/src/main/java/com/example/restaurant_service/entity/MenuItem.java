package com.example.restaurant_service.entity;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MenuItem {

    @Field
    private String id;

    @Field
    private String name;

    @Field
    private String description;

    @Field
    private String category;

    @Field
    private double price;

    @Field
    private boolean available;
}
