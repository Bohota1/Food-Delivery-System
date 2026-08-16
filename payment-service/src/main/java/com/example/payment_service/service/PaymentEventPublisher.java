package com.example.payment_service.service;

import com.example.payment_service.Constants;
import com.example.payment_service.entity.Payment;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes the payment confirmation event back onto RabbitMQ.
 * Order Service consumes it and moves the order from PENDING_PAYMENT to CONFIRMED.
 *
 * <p>Sent as a plain Map (like the order event) so Payment Service and Order Service
 * share only an agreed set of keys, not a Java class.
 */
@Component
public class PaymentEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publishPaymentResult(Payment payment) {
        Map<String, Object> paymentEvent = new HashMap<>();
        paymentEvent.put("orderId", payment.getOrderId());
        paymentEvent.put("paymentId", payment.getId());
        paymentEvent.put("customerId", payment.getCustomerId());
        paymentEvent.put("amount", payment.getAmount());
        paymentEvent.put("status", payment.getStatus());
        paymentEvent.put("transactionId", payment.getTransactionId());
        paymentEvent.put("failureReason", payment.getFailureReason());

        rabbitTemplate.convertAndSend(Constants.PAYMENT_EXCHANGE, Constants.PAYMENT_ROUTING_KEY, paymentEvent);

        System.out.println("Payment confirmation published to queue: " + paymentEvent);
    }
}
