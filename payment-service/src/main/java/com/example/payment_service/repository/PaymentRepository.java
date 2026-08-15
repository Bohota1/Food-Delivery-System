package com.example.payment_service.repository;

import com.example.payment_service.entity.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    Payment findPaymentById(String id);

    Payment findPaymentByOrderId(String orderId);
}
