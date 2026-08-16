package com.fooddelivery.deliveryservice.repository;

import com.fooddelivery.deliveryservice.model.Rider;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RiderRepository extends MongoRepository<Rider, String> {

    List<Rider> findByAvailableTrue();
}
