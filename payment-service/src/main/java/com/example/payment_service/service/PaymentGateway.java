package com.example.payment_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated payment gateway (Stripe/PayPal stand-in), as scoped in the proposal.
 * The outcome is deterministic so the demo never fails by accident:
 * an amount above payment.gateway.max-amount (or a non-positive amount) is declined,
 * everything else is approved.
 */
@Component
public class PaymentGateway {

    @Value("${payment.gateway.max-amount:100000}")
    private double maxAmount;

    public GatewayResult charge(String orderId, double amount) {
        if (amount <= 0) {
            return GatewayResult.decline("Invalid amount: " + amount);
        }
        if (amount > maxAmount) {
            return GatewayResult.decline("Amount " + amount + " exceeds gateway limit of " + maxAmount);
        }
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return GatewayResult.approve(transactionId);
    }

    public record GatewayResult(boolean approved, String transactionId, String failureReason) {

        static GatewayResult approve(String transactionId) {
            return new GatewayResult(true, transactionId, null);
        }

        static GatewayResult decline(String failureReason) {
            return new GatewayResult(false, null, failureReason);
        }
    }
}
