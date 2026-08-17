package com.fooddelivery.deliveryservice.messaging;

import com.fooddelivery.deliveryservice.config.RabbitMQConfig;
import com.fooddelivery.deliveryservice.dto.CreateDeliveryRequest;
import com.fooddelivery.deliveryservice.dto.DeliveryResponse;
import com.fooddelivery.deliveryservice.dto.OrderConfirmedEvent;
import com.fooddelivery.deliveryservice.exception.InvalidStateException;
import com.fooddelivery.deliveryservice.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final DeliveryService deliveryService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CONFIRMED_QUEUE)
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received order.confirmed event for orderId={}", event.getOrderId());

        CreateDeliveryRequest request = new CreateDeliveryRequest();
        request.setOrderId(event.getOrderId());
        request.setCustomerId(event.getCustomerId());
        request.setRestaurantId(event.getRestaurantId());
        request.setDeliveryAddress(event.getDeliveryAddress());
        request.setPickupAddress(event.getPickupAddress());

        try {
            DeliveryResponse delivery = deliveryService.create(request);
            log.info("Delivery record created for orderId={}", event.getOrderId());

            // Rider assignment belongs to Delivery Service, so a confirmed order gets a
            // rider without any further call from outside.
            deliveryService.autoAssignRider(delivery.getId());
        } catch (InvalidStateException alreadyExists) {
            // Duplicate delivery events (e.g. redelivery on broker retry) are safe to ignore.
            log.warn("Skipped duplicate delivery creation for orderId={}", event.getOrderId());
        }
    }
}
