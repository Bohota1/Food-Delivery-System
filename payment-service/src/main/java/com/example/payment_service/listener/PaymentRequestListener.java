package com.example.payment_service.listener;

import com.example.payment_service.Constants;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.service.PaymentEventPublisher;
import com.example.payment_service.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes payment requests published by Order Service on order_queue,
 * processes them, and publishes the result back on payment_exchange.
 *
 * <p>This is the asynchronous half of Payment Service - the customer never calls it directly.
 */
@Component
public class PaymentRequestListener {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @RabbitListener(queues = Constants.ORDER_QUEUE)
    public void consumePaymentRequest(Map<String, Object> orderEvent) {
        System.out.println("Payment request received from queue: " + orderEvent);

        String orderId = asString(orderEvent.get("orderId"));
        String customerId = asString(orderEvent.get("customerId"));
        double amount = resolveAmount(orderEvent);

        if (orderId == null) {
            System.out.println("Ignoring message with no orderId: " + orderEvent);
            return;
        }

        Payment payment = paymentService.processPayment(orderId, customerId, amount);

        if (Constants.SUCCESS.equals(payment.getStatus())) {
            System.out.println("Payment " + payment.getId() + " SUCCESS for order " + orderId
                    + " (amount " + payment.getAmount() + ", txn " + payment.getTransactionId() + ")");
        } else {
            System.out.println("Payment " + payment.getId() + " FAILED for order " + orderId
                    + " - " + payment.getFailureReason());
        }

        paymentEventPublisher.publishPaymentResult(payment);
    }

    /**
     * Order Service sends the order total as "amount"; "price" is accepted as a
     * fallback so the listener still works against the original order event shape.
     */
    private double resolveAmount(Map<String, Object> orderEvent) {
        Object raw = orderEvent.containsKey("amount") ? orderEvent.get("amount") : orderEvent.get("price");
        return (raw instanceof Number number) ? number.doubleValue() : 0d;
    }

    private String asString(Object value) {
        return (value == null) ? null : value.toString();
    }
}
