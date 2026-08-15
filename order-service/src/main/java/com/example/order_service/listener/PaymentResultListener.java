package com.example.order_service.listener;

import com.example.order_service.Constants;
import com.example.order_service.entity.Order;
import com.example.order_service.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes payment confirmations published by Payment Service on payment_queue
 * and moves the order out of PENDING_PAYMENT.
 */
@Component
public class PaymentResultListener {

    @Autowired
    private OrderService orderService;

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
        }

        // Next step in the workflow: when the order is CONFIRMED, publish an
        // order-confirmed event for Delivery Service to consume and assign a rider.
    }

    private String asString(Object value) {
        return (value == null) ? null : value.toString();
    }
}
