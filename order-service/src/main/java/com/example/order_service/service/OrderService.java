package com.example.order_service.service;

import com.example.order_service.Constants;
import com.example.order_service.entity.Order;
import com.example.order_service.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;

    public Order saveOrder(Order order) {
        // A new order is never confirmed until Payment Service reports back over RabbitMQ.
        order.setStatus(Constants.PENDING_PAYMENT);
        order.setPaymentStatus(Constants.PAYMENT_PENDING);

        if (order.getItems() == null) {
            order.setItems(new ArrayList<>());
        }
        // When a cart was sent, the order total is derived from it rather than trusted
        // from the client, so Payment Service can never be told the wrong amount.
        if (!order.getItems().isEmpty()) {
            double total = order.getItems().stream()
                    .mapToDouble(item -> item.getUnitPrice() * item.getQuantity())
                    .sum();
            order.setPrice(total);
        }
        return orderRepository.save(order);
    }

    /**
     * Cancels an order at the customer's request.
     *
     * <p>Only orders that have not already been delivered or cancelled may be cancelled.
     */
    public Order cancelOrder(String orderId, String reason) {
        Order order = orderRepository.findOrderById(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId);
        }
        if (Constants.CANCELLED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already cancelled");
        }
        if (Constants.DELIVERED.equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A delivered order cannot be cancelled");
        }

        order.setStatus(Constants.CANCELLED);
        order.setCancelReason((reason == null || reason.isBlank()) ? "Cancelled by customer" : reason);
        return orderRepository.save(order);
    }

    /** True when the customer already paid, so cancelling owes them a refund. */
    public boolean isRefundable(Order order) {
        return Constants.PAYMENT_SUCCESS.equals(order.getPaymentStatus());
    }

    /**
     * Applies a payment update received from Payment Service.
     *
     * <p>SUCCESS confirms the order, FAILED marks it PAYMENT_FAILED, REFUNDED only records
     * the refund. A cancelled order keeps its status either way - a late payment message
     * must never bring a cancelled order back to life.
     */
    public Order applyPaymentResult(String orderId, String paymentId, String paymentStatus) {
        Order order = orderRepository.findOrderById(orderId);
        if (order == null) {
            System.out.println("No order found for id " + orderId + ", ignoring payment result.");
            return null;
        }

        order.setPaymentId(paymentId);
        order.setPaymentStatus(paymentStatus);

        boolean cancelled = Constants.CANCELLED.equals(order.getStatus());
        boolean refund = Constants.PAYMENT_REFUNDED.equals(paymentStatus);

        if (!cancelled && !refund) {
            order.setStatus(Constants.PAYMENT_SUCCESS.equals(paymentStatus)
                    ? Constants.CONFIRMED
                    : Constants.PAYMENT_FAILED);
        }

        return orderRepository.save(order);
    }

    public Order findOrderById(String id) {
        return orderRepository.findOrderById(id);
    }

    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }

    /** Order history for one customer, newest first. */
    public List<Order> findOrdersByCustomer(String customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        orders.sort((a, b) -> b.getId().compareTo(a.getId()));
        return orders;
    }
}
