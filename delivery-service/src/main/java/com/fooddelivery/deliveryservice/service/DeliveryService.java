package com.fooddelivery.deliveryservice.service;

import com.fooddelivery.deliveryservice.config.RabbitMQConfig;
import com.fooddelivery.deliveryservice.dto.*;
import com.fooddelivery.deliveryservice.exception.InvalidStateException;
import com.fooddelivery.deliveryservice.exception.NoAvailableRiderException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.model.Delivery;
import com.fooddelivery.deliveryservice.model.DeliveryStatus;
import com.fooddelivery.deliveryservice.model.Rider;
import com.fooddelivery.deliveryservice.repository.DeliveryRepository;
import com.fooddelivery.deliveryservice.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final RiderRepository riderRepository;
    private final RabbitTemplate rabbitTemplate;

    // Legal "manual" transitions via the status-update endpoint. ASSIGNED is
    // reached only through the dedicated assign-rider endpoint, not through
    // this map.
    private static final Map<DeliveryStatus, EnumSet<DeliveryStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(DeliveryStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(DeliveryStatus.PENDING, EnumSet.of(DeliveryStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.ASSIGNED, EnumSet.of(DeliveryStatus.PICKED_UP, DeliveryStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.PICKED_UP, EnumSet.of(DeliveryStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.DELIVERED, EnumSet.noneOf(DeliveryStatus.class));
        ALLOWED_TRANSITIONS.put(DeliveryStatus.CANCELLED, EnumSet.noneOf(DeliveryStatus.class));
    }

    /** Creates a delivery record. Called either via REST (manual/demo) or by the RabbitMQ listener. */
    public DeliveryResponse create(CreateDeliveryRequest request) {
        if (deliveryRepository.existsByOrderId(request.getOrderId())) {
            throw new InvalidStateException("A delivery already exists for orderId: " + request.getOrderId());
        }

        Delivery delivery = Delivery.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .restaurantId(request.getRestaurantId())
                .deliveryAddress(request.getDeliveryAddress())
                .pickupAddress(request.getPickupAddress())
                .status(DeliveryStatus.PENDING)
                .build();

        Delivery saved = deliveryRepository.save(delivery);
        publishStatus(saved);   // "delivery placed" - the first of the four delivery events
        return toResponse(saved);
    }

    public DeliveryResponse assignRider(String deliveryId, AssignRiderRequest request) {
        Delivery delivery = findOrThrow(deliveryId);

        if (delivery.getStatus() != DeliveryStatus.PENDING) {
            throw new InvalidStateException("Delivery " + deliveryId + " is not PENDING, cannot assign a rider");
        }

        Rider rider = (request.getRiderId() != null && !request.getRiderId().isBlank())
                ? riderRepository.findById(request.getRiderId())
                    .filter(Rider::isAvailable)
                    .orElseThrow(() -> new NoAvailableRiderException("Requested rider is not available: " + request.getRiderId()))
                : riderRepository.findByAvailableTrue().stream().findFirst()
                    .orElseThrow(() -> new NoAvailableRiderException("No available riders at the moment"));

        rider.setAvailable(false);
        rider.setCurrentDeliveryId(delivery.getId());
        riderRepository.save(rider);

        delivery.setRiderId(rider.getId());
        delivery.setStatus(DeliveryStatus.ASSIGNED);
        delivery.setAssignedAt(LocalDateTime.now());

        Delivery saved = deliveryRepository.save(delivery);
        publishStatus(saved);
        return toResponse(saved);
    }

    /**
     * Picks the first available rider for a delivery that was just created from an
     * order.confirmed event. Having no rider free is a normal business state, not an
     * error - the delivery simply stays PENDING until someone is assigned manually.
     */
    public void autoAssignRider(String deliveryId) {
        try {
            DeliveryResponse assigned = assignRider(deliveryId, new AssignRiderRequest());
            log.info("Auto-assigned rider {} to delivery {}", assigned.getRiderId(), assigned.getId());
        } catch (NoAvailableRiderException e) {
            log.warn("Delivery {} stays PENDING: {}", deliveryId, e.getMessage());
        }
    }

    /**
     * Rider finished the job: delivery becomes DELIVERED and the rider is freed for
     * the next order. Same state machine as the status endpoint, so a delivery that
     * was never picked up cannot jump straight to delivered.
     */
    public DeliveryResponse complete(String deliveryId) {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(DeliveryStatus.DELIVERED);
        return updateStatus(deliveryId, request);
    }

    public DeliveryResponse updateStatus(String deliveryId, UpdateStatusRequest request) {
        Delivery delivery = findOrThrow(deliveryId);
        DeliveryStatus current = delivery.getStatus();
        DeliveryStatus next = request.getStatus();

        EnumSet<DeliveryStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(DeliveryStatus.class));
        if (!allowed.contains(next)) {
            throw new InvalidStateException(
                    "Cannot transition delivery " + deliveryId + " from " + current + " to " + next);
        }

        switch (next) {
            case PICKED_UP -> delivery.setPickedUpAt(LocalDateTime.now());
            case DELIVERED -> {
                delivery.setDeliveredAt(LocalDateTime.now());
                freeRider(delivery.getRiderId());
            }
            case CANCELLED -> {
                delivery.setCancelledAt(LocalDateTime.now());
                freeRider(delivery.getRiderId());
            }
            default -> { /* no side effect */ }
        }

        delivery.setStatus(next);
        Delivery saved = deliveryRepository.save(delivery);
        publishStatus(saved);
        return toResponse(saved);
    }

    /**
     * Tells Order Service how the delivery is progressing, so the customer can see
     * DELIVERED on the order itself without Delivery Service being called directly.
     * The delivery is already saved, so a broker outage is logged, never rethrown.
     */
    private void publishStatus(Delivery delivery) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", delivery.getOrderId());
        event.put("deliveryId", delivery.getId());
        event.put("riderId", delivery.getRiderId());
        event.put("status", delivery.getStatus().name());

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.DELIVERY_EXCHANGE,
                    RabbitMQConfig.DELIVERY_STATUS_ROUTING_KEY, event);
            log.info("Published delivery event [{}] for orderId={} — {}",
                    delivery.getStatus(), delivery.getOrderId(), describe(delivery.getStatus()));
        } catch (AmqpException e) {
            log.warn("Could not publish delivery status for orderId={}: {}", delivery.getOrderId(), e.getMessage());
        }
    }

    public DeliveryResponse getById(String deliveryId) {
        return toResponse(findOrThrow(deliveryId));
    }

    public DeliveryResponse getByOrderId(String orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery found for orderId: " + orderId));
        return toResponse(delivery);
    }

    public List<DeliveryResponse> getByRider(String riderId) {
        return deliveryRepository.findByRiderId(riderId).stream().map(this::toResponse).toList();
    }

    private void freeRider(String riderId) {
        if (riderId == null) return;
        riderRepository.findById(riderId).ifPresent(rider -> {
            rider.setAvailable(true);
            rider.setCurrentDeliveryId(null);
            riderRepository.save(rider);
        });
    }

    /** Plain-English label for each delivery event, so the console reads as a story. */
    private String describe(DeliveryStatus status) {
        return switch (status) {
            case PENDING -> "delivery placed, waiting for a rider";
            case ASSIGNED -> "handed to a rider";
            case PICKED_UP -> "rider collected the food";
            case DELIVERED -> "delivered successfully, rider free again";
            case CANCELLED -> "delivery called off, rider free again";
        };
    }

    private Delivery findOrThrow(String deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + deliveryId));
    }

    private DeliveryResponse toResponse(Delivery d) {
        return DeliveryResponse.builder()
                .id(d.getId())
                .orderId(d.getOrderId())
                .customerId(d.getCustomerId())
                .restaurantId(d.getRestaurantId())
                .riderId(d.getRiderId())
                .status(d.getStatus())
                .deliveryAddress(d.getDeliveryAddress())
                .pickupAddress(d.getPickupAddress())
                .assignedAt(d.getAssignedAt())
                .pickedUpAt(d.getPickedUpAt())
                .deliveredAt(d.getDeliveredAt())
                .cancelledAt(d.getCancelledAt())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
