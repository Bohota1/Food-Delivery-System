package com.example.order_service.service;

import com.example.order_service.Constants;
import com.example.order_service.entity.Order;
import com.example.order_service.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;

    public Order saveOrder(Order order) {
        // A new order is never confirmed until Payment Service reports back over RabbitMQ.
        order.setStatus(Constants.PENDING_PAYMENT);
        order.setPaymentStatus(Constants.PAYMENT_PENDING);
        return orderRepository.save(order);
    }

    /**
     * Applies the payment confirmation received from Payment Service.
     * SUCCESS confirms the order; FAILED marks it PAYMENT_FAILED.
     */
    public Order applyPaymentResult(String orderId, String paymentId, String paymentStatus) {
        Order order = orderRepository.findOrderById(orderId);
        if (order == null) {
            System.out.println("No order found for id " + orderId + ", ignoring payment result.");
            return null;
        }

        order.setPaymentId(paymentId);
        order.setPaymentStatus(paymentStatus);
        order.setStatus(Constants.PAYMENT_SUCCESS.equals(paymentStatus)
                ? Constants.CONFIRMED
                : Constants.PAYMENT_FAILED);

        return orderRepository.save(order);
    }

    public Order findOrderById(String id) {
        return orderRepository.findOrderById(id);
    }

    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }
}