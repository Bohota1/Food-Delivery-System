package com.example.payment_service.service;

import com.example.payment_service.Constants;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.service.PaymentGateway.GatewayResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGateway paymentGateway;

    /**
     * Records the payment as PENDING, runs it through the simulated gateway,
     * then saves the final SUCCESS/FAILED result.
     *
     * <p>Idempotent on orderId: if the order was already paid successfully the existing
     * payment is returned untouched, so a redelivered RabbitMQ message never charges twice.
     */
    public Payment processPayment(String orderId, String customerId, double amount) {
        Payment existing = paymentRepository.findPaymentByOrderId(orderId);
        if (existing != null && Constants.SUCCESS.equals(existing.getStatus())) {
            System.out.println("Order " + orderId + " is already paid (payment " + existing.getId() + "), skipping.");
            return existing;
        }

        Payment payment = (existing != null) ? existing : new Payment();
        payment.setOrderId(orderId);
        payment.setCustomerId(customerId);
        payment.setAmount(amount);
        payment.setMethod("SIMULATED_GATEWAY");
        payment.setStatus(Constants.PENDING);
        payment.setTransactionId(null);
        payment.setFailureReason(null);
        payment.setProcessedAt(null);
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(Instant.now());
        }
        payment = paymentRepository.save(payment);

        GatewayResult result = paymentGateway.charge(orderId, amount);
        if (result.approved()) {
            payment.setStatus(Constants.SUCCESS);
            payment.setTransactionId(result.transactionId());
        } else {
            payment.setStatus(Constants.FAILED);
            payment.setFailureReason(result.failureReason());
        }
        payment.setProcessedAt(Instant.now());

        return paymentRepository.save(payment);
    }

    /**
     * Refunds a successful payment - the "Request Refund" use case.
     *
     * <p>Only a SUCCESS payment can be refunded. Refunding one that is already REFUNDED is
     * treated as a no-op rather than an error, so a redelivered refund message is harmless.
     */
    public Payment refundPayment(Payment payment, String reason) {
        if (Constants.REFUNDED.equals(payment.getStatus())) {
            System.out.println("Payment " + payment.getId() + " is already refunded, skipping.");
            return payment;
        }
        if (!Constants.SUCCESS.equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a successful payment can be refunded (current status: " + payment.getStatus() + ")");
        }

        payment.setStatus(Constants.REFUNDED);
        payment.setRefundReason((reason == null || reason.isBlank()) ? "Refund requested by customer" : reason);
        payment.setRefundedAt(Instant.now());
        return paymentRepository.save(payment);
    }

    public Payment requireById(String id) {
        Payment payment = paymentRepository.findPaymentById(id);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + id);
        }
        return payment;
    }

    public Payment requireByOrderId(String orderId) {
        Payment payment = paymentRepository.findPaymentByOrderId(orderId);
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for order: " + orderId);
        }
        return payment;
    }

    public Payment findPaymentById(String id) {
        return paymentRepository.findPaymentById(id);
    }

    public Payment findPaymentByOrderId(String orderId) {
        return paymentRepository.findPaymentByOrderId(orderId);
    }

    public List<Payment> findAllPayments() {
        return paymentRepository.findAll();
    }
}
