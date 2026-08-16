package com.example.order_service.repository;

import com.example.order_service.entity.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    Order findOrderById(String id);
    List<Order> findByCustomerId(String customerId);
}
