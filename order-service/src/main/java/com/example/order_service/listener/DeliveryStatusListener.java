package com.example.order_service.listener;

import com.example.order_service.Constants;
import com.example.order_service.entity.Order;
import com.example.order_service.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes delivery progress published by Delivery Service on delivery.status.queue.
 *
 * <p>This is the last hop of the workflow: once the rider completes the delivery,
 * the order itself becomes DELIVERED, so a single GET /orders/{id} shows the whole
 * story - payment, delivery and order status together.
 */
@Component
public class DeliveryStatusListener {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = Constants.DELIVERY_STATUS_QUEUE)
    public void consumeDeliveryStatus(Map<String, Object> deliveryEvent) {
        System.out.println("Delivery status received from queue: " + deliveryEvent);

        String orderId = asString(deliveryEvent.get("orderId"));
        String deliveryId = asString(deliveryEvent.get("deliveryId"));
        String riderId = asString(deliveryEvent.get("riderId"));
        String status = asString(deliveryEvent.get("status"));

        if (orderId == null) {
            System.out.println("Ignoring delivery event with no orderId: " + deliveryEvent);
            return;
        }

        Order order = orderService.applyDeliveryStatus(orderId, deliveryId, riderId, status);
        if (order != null) {
            System.out.println("Order " + orderId + " delivery is now " + status
                    + " (order status " + order.getStatus() + ")");
        }
    }

    private String asString(Object value) {
        return (value == null) ? null : value.toString();
    }
}
