package com.fooddelivery.userservice.model;

/**
 * The two kinds of account in the system. Riders are not users - they live in
 * Delivery Service and are registered by an Admin.
 */
public final class Roles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }

    /** Anything unrecognised (including null, i.e. accounts created before roles existed) is a customer. */
    public static String normalise(String role) {
        return ADMIN.equalsIgnoreCase(String.valueOf(role).trim()) ? ADMIN : CUSTOMER;
    }
}
