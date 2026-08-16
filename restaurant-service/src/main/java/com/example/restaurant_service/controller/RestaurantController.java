package com.example.restaurant_service.controller;

import com.example.restaurant_service.Constants;
import com.example.restaurant_service.entity.MenuItem;
import com.example.restaurant_service.entity.Restaurant;
import com.example.restaurant_service.service.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/restaurants")
public class RestaurantController {
    private static final Logger log = LoggerFactory.getLogger(RestaurantController.class);

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping("/")
    public Restaurant createRestaurant(@RequestBody Restaurant restaurant) {
        return restaurantService.saveRestaurant(restaurant);
    }

    @GetMapping("/")
    public List<Restaurant> findAllRestaurants() {
        return restaurantService.findAllRestaurants();
    }

    @GetMapping("/available")
    public List<Restaurant> findAvailableRestaurants() {
        return restaurantService.findAvailableRestaurants();
    }

    @GetMapping("/search")
    public List<Restaurant> searchByCuisineType(@RequestParam("cuisineType") String cuisineType) {
        return restaurantService.findRestaurantsByCuisineType(cuisineType);
    }

    @GetMapping("/{id}")
    public Restaurant findRestaurantById(@PathVariable("id") String id) {
        return restaurantService.findRestaurantById(id);
    }

    @PutMapping("/{id}")
    public Restaurant updateRestaurant(@PathVariable("id") String id, @RequestBody Restaurant restaurant) {
        return restaurantService.updateRestaurant(id, restaurant);
    }

    @PatchMapping("/{id}/availability")
    public Restaurant setAvailability(@PathVariable("id") String id, @RequestParam("available") boolean available) {
        Restaurant restaurant = restaurantService.setAvailability(id, available);
        publishRestaurantEvent(restaurant, "AVAILABILITY_UPDATED");
        return restaurant;
    }

    @DeleteMapping("/{id}")
    public void deleteRestaurant(@PathVariable("id") String id) {
        restaurantService.deleteRestaurant(id);
    }

    @GetMapping("/{id}/menu")
    public List<MenuItem> getMenuItems(@PathVariable("id") String id) {
        return restaurantService.getMenuItems(id);
    }

    @PostMapping("/{id}/menu")
    public MenuItem addMenuItem(@PathVariable("id") String id, @RequestBody MenuItem menuItem) {
        MenuItem saved = restaurantService.addMenuItem(id, menuItem);
        publishMenuEvent(id, saved, "MENU_ITEM_ADDED");
        return saved;
    }

    @PutMapping("/{id}/menu/{itemId}")
    public MenuItem updateMenuItem(@PathVariable("id") String id, @PathVariable("itemId") String itemId,
                                    @RequestBody MenuItem menuItem) {
        MenuItem updated = restaurantService.updateMenuItem(id, itemId, menuItem);
        publishMenuEvent(id, updated, "MENU_ITEM_UPDATED");
        return updated;
    }

    @PatchMapping("/{id}/menu/{itemId}/availability")
    public MenuItem setMenuItemAvailability(@PathVariable("id") String id, @PathVariable("itemId") String itemId,
                                             @RequestParam("available") boolean available) {
        MenuItem updated = restaurantService.setMenuItemAvailability(id, itemId, available);
        publishMenuEvent(id, updated, "MENU_ITEM_AVAILABILITY_UPDATED");
        return updated;
    }

    @DeleteMapping("/{id}/menu/{itemId}")
    public void deleteMenuItem(@PathVariable("id") String id, @PathVariable("itemId") String itemId) {
        restaurantService.deleteMenuItem(id, itemId);
    }

    private void publishRestaurantEvent(Restaurant restaurant, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("restaurantId", restaurant.getId());
        event.put("available", restaurant.isAvailable());
        publish(event);
    }

    private void publishMenuEvent(String restaurantId, MenuItem menuItem, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("restaurantId", restaurantId);
        event.put("menuItemId", menuItem.getId());
        event.put("available", menuItem.isAvailable());
        publish(event);
    }

    private void publish(Map<String, Object> event) {
        try {
            rabbitTemplate.convertAndSend(Constants.EXCHANGE, Constants.ROUTING_KEY, event);
        } catch (AmqpException e) {
            log.warn("Could not publish event to RabbitMQ, continuing without it: {}", e.getMessage());
        }
    }
}
