package com.fooddelivery.deliveryservice.exception;

public class NoAvailableRiderException extends RuntimeException {
    public NoAvailableRiderException(String message) {
        super(message);
    }
}
