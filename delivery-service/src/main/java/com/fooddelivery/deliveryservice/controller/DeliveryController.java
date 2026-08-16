package com.fooddelivery.deliveryservice.controller;

import com.fooddelivery.deliveryservice.dto.*;
import com.fooddelivery.deliveryservice.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    /**
     * Manually creates a delivery record. In the full system this happens
     * automatically via the RabbitMQ order.confirmed event; this endpoint
     * lets you create/demo deliveries directly (e.g. before Order Service
     * exists, or from Postman).
     */
    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody CreateDeliveryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.create(request));
    }

    @PostMapping("/{deliveryId}/assign")
    public ResponseEntity<DeliveryResponse> assignRider(@PathVariable String deliveryId,
                                                          @RequestBody(required = false) AssignRiderRequest request) {
        AssignRiderRequest body = request != null ? request : new AssignRiderRequest();
        return ResponseEntity.ok(deliveryService.assignRider(deliveryId, body));
    }

    @PutMapping("/{deliveryId}/status")
    public ResponseEntity<DeliveryResponse> updateStatus(@PathVariable String deliveryId,
                                                           @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(deliveryService.updateStatus(deliveryId, request));
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getById(@PathVariable String deliveryId) {
        return ResponseEntity.ok(deliveryService.getById(deliveryId));
    }

    /** Used by the Order Service / customers to check delivery status for a given order. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<DeliveryResponse> getByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(deliveryService.getByOrderId(orderId));
    }

    /** "View Assigned Deliveries" use case for a Delivery Rider. */
    @GetMapping("/rider/{riderId}")
    public ResponseEntity<List<DeliveryResponse>> getByRider(@PathVariable String riderId) {
        return ResponseEntity.ok(deliveryService.getByRider(riderId));
    }
}
