package com.example.restaurant_service.repository;

import com.example.restaurant_service.entity.Restaurant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RestaurantRepository extends MongoRepository<Restaurant, String> {
    Restaurant findRestaurantById(String id);
    List<Restaurant> findByAvailableTrue();
    List<Restaurant> findByCuisineTypeIgnoreCase(String cuisineType);
}
