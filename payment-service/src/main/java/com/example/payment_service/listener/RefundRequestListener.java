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
 * Consumes refund requests published by Order Service when a customer cancels an order
 * they had already paid for, and pushes the refunded payment back onto payment_exchange.
 *
 * <p>The customer can also request a refund directly over REST - see PaymentController.
 * Both paths end up in {@code PaymentService.refundPayment}.
 */
@Component
public class RefundRequestListener {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @RabbitListener(queues = Constants.REFUND_QUEUE)
    public void consumeRefundRequest(Map<String, Object> refundEvent) {
        System.out.println("Refund request received from queue: " + refundEvent);

        String orderId = asString(refundEvent.get("orderId"));
        String reason = asString(refundEvent.get("reason"));

        if (orderId == null) {
            System.out.println("Ignoring refund request with no orderId: " + refundEvent);
            return;
        }

        Payment payment = paymentService.findPaymentByOrderId(orderId);
        if (payment == null) {
            System.out.println("No payment found for order " + orderId + ", nothing to refund.");
            return;
        }

        try {
            Payment refunded = paymentService.refundPayment(payment, reason);
            System.out.println("Payment " + refunded.getId() + " REFUNDED for order " + orderId);
            paymentEventPublisher.publishPaymentResult(refunded);
        } catch (RuntimeException e) {
            // A cancelled order whose payment never succeeded has nothing to refund.
            System.out.println("Refund skipped for order " + orderId + ": " + e.getMessage());
        }
    }

    private String asString(Object value) {
        return (value == null) ? null : value.toString();
    }
}
