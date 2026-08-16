package com.fooddelivery.deliveryservice.model;

/**
 * Discrete delivery status transitions, per the proposal's simplified scope
 * (no live GPS tracking — status moves forward in fixed steps):
 *
 *   PENDING  --assign-->  ASSIGNED  --pickup-->  PICKED_UP  --deliver-->  DELIVERED
 *      \                     /
 *       \-------cancel------/
 */
public enum DeliveryStatus {
    PENDING,
    ASSIGNED,
    PICKED_UP,
    DELIVERED,
    CANCELLED
}
