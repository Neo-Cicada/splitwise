package com.splitwise.support;

/** Thrown when a requested resource does not exist. Rendered as error/404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
