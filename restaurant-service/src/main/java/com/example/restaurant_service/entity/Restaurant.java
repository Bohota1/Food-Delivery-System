package com.example.restaurant_service.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "restaurants")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Restaurant {

    @Id
    private String id;

    @Field
    private String name;

    @Field
    private String address;

    @Field
    private String cuisineType;

    @Field
    private String phone;

    @Field
    private boolean available;

    @Field
    private List<MenuItem> menuItems = new ArrayList<>();
}
