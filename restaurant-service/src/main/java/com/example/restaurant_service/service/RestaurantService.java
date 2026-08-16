package com.example.restaurant_service.service;

import com.example.restaurant_service.entity.MenuItem;
import com.example.restaurant_service.entity.Restaurant;
import com.example.restaurant_service.repository.RestaurantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {
    @Autowired
    private RestaurantRepository restaurantRepository;

    public Restaurant saveRestaurant(Restaurant restaurant) {
        if (restaurant.getMenuItems() == null) {
            restaurant.setMenuItems(new ArrayList<>());
        }
        return restaurantRepository.save(restaurant);
    }

    public List<Restaurant> findAllRestaurants() {
        return restaurantRepository.findAll();
    }

    public List<Restaurant> findAvailableRestaurants() {
        return restaurantRepository.findByAvailableTrue();
    }

    public List<Restaurant> findRestaurantsByCuisineType(String cuisineType) {
        return restaurantRepository.findByCuisineTypeIgnoreCase(cuisineType);
    }

    public Restaurant findRestaurantById(String id) {
        Restaurant restaurant = restaurantRepository.findRestaurantById(id);
        if (restaurant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant not found: " + id);
        }
        return restaurant;
    }

    public Restaurant updateRestaurant(String id, Restaurant update) {
        Restaurant restaurant = findRestaurantById(id);
        restaurant.setName(update.getName());
        restaurant.setAddress(update.getAddress());
        restaurant.setCuisineType(update.getCuisineType());
        restaurant.setPhone(update.getPhone());
        return restaurantRepository.save(restaurant);
    }

    public Restaurant setAvailability(String id, boolean available) {
        Restaurant restaurant = findRestaurantById(id);
        restaurant.setAvailable(available);
        return restaurantRepository.save(restaurant);
    }

    public void deleteRestaurant(String id) {
        Restaurant restaurant = findRestaurantById(id);
        restaurantRepository.delete(restaurant);
    }

    public List<MenuItem> getMenuItems(String restaurantId) {
        return findRestaurantById(restaurantId).getMenuItems();
    }

    public MenuItem addMenuItem(String restaurantId, MenuItem menuItem) {
        Restaurant restaurant = findRestaurantById(restaurantId);
        menuItem.setId(UUID.randomUUID().toString());
        restaurant.getMenuItems().add(menuItem);
        restaurantRepository.save(restaurant);
        return menuItem;
    }

    public MenuItem updateMenuItem(String restaurantId, String itemId, MenuItem update) {
        Restaurant restaurant = findRestaurantById(restaurantId);
        MenuItem menuItem = findMenuItem(restaurant, itemId);
        menuItem.setName(update.getName());
        menuItem.setDescription(update.getDescription());
        menuItem.setCategory(update.getCategory());
        menuItem.setPrice(update.getPrice());
        restaurantRepository.save(restaurant);
        return menuItem;
    }

    public MenuItem setMenuItemAvailability(String restaurantId, String itemId, boolean available) {
        Restaurant restaurant = findRestaurantById(restaurantId);
        MenuItem menuItem = findMenuItem(restaurant, itemId);
        menuItem.setAvailable(available);
        restaurantRepository.save(restaurant);
        return menuItem;
    }

    public void deleteMenuItem(String restaurantId, String itemId) {
        Restaurant restaurant = findRestaurantById(restaurantId);
        boolean removed = restaurant.getMenuItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found: " + itemId);
        }
        restaurantRepository.save(restaurant);
    }

    private MenuItem findMenuItem(Restaurant restaurant, String itemId) {
        return restaurant.getMenuItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item not found: " + itemId));
    }
}
