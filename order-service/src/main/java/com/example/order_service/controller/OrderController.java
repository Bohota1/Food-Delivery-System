package com.example.order_service.controller;

import com.example.order_service.Constants;
import com.example.order_service.entity.Order;
import com.example.order_service.service.OrderService;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostMapping("/")
    public Order saveOrder(@RequestBody Order order) {
        Order savedOrder = orderService.saveOrder(order);

        Map<String, Object> orderEvent = new HashMap<>();
        orderEvent.put("orderId", savedOrder.getId());
        orderEvent.put("customerId", savedOrder.getCustomerId());
        orderEvent.put("restaurantId", savedOrder.getRestaurantId());
        orderEvent.put("productId", savedOrder.getProductId());
        orderEvent.put("quantity", savedOrder.getQuantity());
        orderEvent.put("price", savedOrder.getPrice());
        // Explicit key so Payment Service never has to guess what to charge.
        orderEvent.put("amount", savedOrder.getPrice());

        rabbitTemplate.convertAndSend(Constants.EXCHANGE, Constants.ROUTING_KEY, orderEvent);

        return savedOrder;
    }

    @GetMapping("/{id}")
    public Order findOrderById(@PathVariable("id") String orderId) {
        return orderService.findOrderById(orderId);
    }

    @GetMapping("/")
    public List<Order> findAllOrders() {
        return orderService.findAllOrders();
    }

    /** Order history for one customer - what the website's "My orders" page shows. */
    @GetMapping("/customer/{customerId}")
    public List<Order> findOrdersByCustomer(@PathVariable("customerId") String customerId) {
        return orderService.findOrdersByCustomer(customerId);
    }

    /**
     * Cancels an order. If the customer had already paid, a refund request is published
     * for Payment Service; either way Delivery Service is told to call off the delivery.
     */
    @PostMapping("/{id}/cancel")
    public Order cancelOrder(@PathVariable("id") String orderId,
                             @RequestBody(required = false) Map<String, Object> payload) {
        String reason = (payload == null) ? null : String.valueOf(payload.getOrDefault("reason", ""));
        Order order = orderService.cancelOrder(orderId, reason);

        if (orderService.isRefundable(order)) {
            Map<String, Object> refund = new HashMap<>();
            refund.put("orderId", order.getId());
            refund.put("paymentId", order.getPaymentId());
            refund.put("customerId", order.getCustomerId());
            refund.put("amount", order.getPrice());
            refund.put("reason", order.getCancelReason());
            publish(Constants.REFUND_EXCHANGE, Constants.REFUND_ROUTING_KEY, refund,
                    "refund request for order " + order.getId());
        }

        Map<String, Object> cancelled = new HashMap<>();
        cancelled.put("orderId", order.getId());
        cancelled.put("reason", order.getCancelReason());
        publish(Constants.ORDER_CONFIRMED_EXCHANGE, Constants.ORDER_CANCELLED_ROUTING_KEY, cancelled,
                "order-cancelled event for order " + order.getId());

        return order;
    }

    /**
     * The order is already saved by the time we publish, so a broker outage is logged
     * rather than allowed to fail the customer's request.
     */
    private void publish(String exchange, String routingKey, Map<String, Object> event, String what) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            System.out.println("Published " + what);
        } catch (AmqpException e) {
            System.out.println("Could not publish " + what + ": " + e.getMessage());
        }
    }
}