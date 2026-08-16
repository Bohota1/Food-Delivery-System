package com.fooddelivery.deliveryservice.repository;

import com.fooddelivery.deliveryservice.model.Delivery;
import com.fooddelivery.deliveryservice.model.DeliveryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends MongoRepository<Delivery, String> {

    Optional<Delivery> findByOrderId(String orderId);

    List<Delivery> findByRiderId(String riderId);

    List<Delivery> findByRiderIdAndStatus(String riderId, DeliveryStatus status);

    boolean existsByOrderId(String orderId);
}
