package com.example.order_service.listener;

import com.example.order_service.Constants;
import com.example.order_service.entity.Order;
import com.example.order_service.service.OrderService;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes payment confirmations published by Payment Service on payment_queue
 * and moves the order out of PENDING_PAYMENT.
 *
 * <p>When payment succeeded the order becomes CONFIRMED, and this class then
 * publishes an order-confirmed event that Delivery Service consumes to create a
 * delivery record and assign a rider.
 */
@Component
public class PaymentResultListener {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = Constants.PAYMENT_QUEUE)
    public void consumePaymentResult(Map<String, Object> paymentEvent) {
        System.out.println("Payment confirmation received from queue: " + paymentEvent);

        String orderId = asString(paymentEvent.get("orderId"));
        String paymentId = asString(paymentEvent.get("paymentId"));
        String paymentStatus = asString(paymentEvent.get("status"));

        if (orderId == null) {
            System.out.println("Ignoring payment event with no orderId: " + paymentEvent);
            return;
        }

        Order order = orderService.applyPaymentResult(orderId, paymentId, paymentStatus);
        if (order != null) {
            System.out.println("Order " + orderId + " is now " + order.getStatus());

            if (Constants.CONFIRMED.equals(order.getStatus())) {
                publishOrderConfirmed(order);
            }
        }
    }

    /**
     * Hands the confirmed order off to Delivery Service. The field names below must
     * match Delivery Service's OrderConfirmedEvent, which is how its listener
     * deserializes the message.
     */
    private void publishOrderConfirmed(Order order) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", order.getId());
        event.put("customerId", order.getCustomerId());
        event.put("restaurantId", order.getRestaurantId());
        event.put("deliveryAddress", order.getDeliveryAddress());
        event.put("pickupAddress", order.getPickupAddress());

        try {
            rabbitTemplate.convertAndSend(Constants.ORDER_CONFIRMED_EXCHANGE,
                    Constants.ORDER_CONFIRMED_ROUTING_KEY, event);
            System.out.println("Published order-confirmed event for order " + order.getId());
        } catch (AmqpException e) {
            // The order is already CONFIRMED in the database; losing the broker must not
            // undo that. The event is skipped and logged instead.
            System.out.println("Could not publish order-confirmed event for order "
                    + order.getId() + ": " + e.getMessage());
        }
    }

    private String asString(Object value) {
        return (value == null) ? null : value.toString();
    }
}
