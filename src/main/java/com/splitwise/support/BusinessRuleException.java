package com.splitwise.support;

/**
 * Thrown when a request is well-formed but violates a domain rule (for example
 * adding a member who is already in the group). Controllers turn these into a
 * flash error and redirect, rather than an error page.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
