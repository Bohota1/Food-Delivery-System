package com.example.payment_service.controller;

import com.example.payment_service.entity.Payment;
import com.example.payment_service.service.PaymentEventPublisher;
import com.example.payment_service.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Synchronous half of Payment Service: verification endpoints used by the customer
 * (via the API Gateway) and by Order Service to look payments up.
 *
 * <p>The normal payment path is asynchronous - see {@code PaymentRequestListener}.
 * The POST endpoint here is a manual initiation/replay hook, not the customer checkout flow.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

    @GetMapping("/")
    public List<Payment> findAllPayments() {
        return paymentService.findAllPayments();
    }

    @GetMapping("/{id}")
    public Payment findPaymentById(@PathVariable("id") String paymentId) {
        return paymentService.findPaymentById(paymentId);
    }

    @GetMapping("/order/{orderId}")
    public Payment findPaymentByOrderId(@PathVariable("orderId") String orderId) {
        return paymentService.findPaymentByOrderId(orderId);
    }

    @PostMapping("/")
    public Payment initiatePayment(@RequestBody PaymentRequest request) {
        Payment payment = paymentService.processPayment(
                request.orderId(), request.customerId(), request.amount());
        paymentEventPublisher.publishPaymentResult(payment);
        return payment;
    }

    /**
     * "Request Refund" - the customer asks for their money back on a payment.
     * Order Service is told over RabbitMQ so the order reflects the refund.
     */
    @PostMapping("/{id}/refund")
    public Payment refundPayment(@PathVariable("id") String paymentId,
                                 @RequestBody(required = false) RefundRequest request) {
        Payment payment = paymentService.requireById(paymentId);
        Payment refunded = paymentService.refundPayment(payment, request == null ? null : request.reason());
        paymentEventPublisher.publishPaymentResult(refunded);
        return refunded;
    }

    /** Same refund, addressed by order instead of payment id - what the website uses. */
    @PostMapping("/order/{orderId}/refund")
    public Payment refundByOrder(@PathVariable("orderId") String orderId,
                                 @RequestBody(required = false) RefundRequest request) {
        Payment payment = paymentService.requireByOrderId(orderId);
        Payment refunded = paymentService.refundPayment(payment, request == null ? null : request.reason());
        paymentEventPublisher.publishPaymentResult(refunded);
        return refunded;
    }

    public record PaymentRequest(String orderId, String customerId, double amount) {
    }

    public record RefundRequest(String reason) {
    }
}
