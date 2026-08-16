package com.example.order_service.entity;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One line of a customer's cart. Embedded inside {@link Order} - a line item has no
 * meaning outside the order it belongs to, so it is not a document of its own.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem {

    @Field
    private String menuItemId;

    @Field
    private String name;

    @Field
    private int quantity;

    @Field
    private double unitPrice;
}
