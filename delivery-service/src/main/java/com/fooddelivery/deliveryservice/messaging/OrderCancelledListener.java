package com.fooddelivery.deliveryservice.messaging;

import com.fooddelivery.deliveryservice.config.RabbitMQConfig;
import com.fooddelivery.deliveryservice.dto.DeliveryResponse;
import com.fooddelivery.deliveryservice.dto.UpdateStatusRequest;
import com.fooddelivery.deliveryservice.exception.InvalidStateException;
import com.fooddelivery.deliveryservice.exception.ResourceNotFoundException;
import com.fooddelivery.deliveryservice.model.DeliveryStatus;
import com.fooddelivery.deliveryservice.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Calls off a delivery when the customer cancels the order behind it, which also frees
 * the assigned rider. A delivery already picked up cannot be cancelled - by then the food
 * is on its way, and the rider still has to complete the trip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledListener {

    private final DeliveryService deliveryService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCELLED_QUEUE)
    public void handleOrderCancelled(Map<String, Object> event) {
        Object orderId = event.get("orderId");
        if (orderId == null) {
            log.warn("Ignoring order.cancelled event with no orderId: {}", event);
            return;
        }
        log.info("Received order.cancelled event for orderId={}", orderId);

        try {
            DeliveryResponse delivery = deliveryService.getByOrderId(orderId.toString());

            UpdateStatusRequest request = new UpdateStatusRequest();
            request.setStatus(DeliveryStatus.CANCELLED);
            deliveryService.updateStatus(delivery.getId(), request);

            log.info("Delivery {} cancelled for orderId={}", delivery.getId(), orderId);
        } catch (ResourceNotFoundException noDelivery) {
            // Cancelled before the order was ever confirmed, so no delivery exists yet.
            log.info("No delivery exists for orderId={}, nothing to cancel", orderId);
        } catch (InvalidStateException tooLate) {
            log.warn("Delivery for orderId={} can no longer be cancelled: {}", orderId, tooLate.getMessage());
        }
    }
}
