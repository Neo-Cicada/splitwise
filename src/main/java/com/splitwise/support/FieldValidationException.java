package com.splitwise.support;

/**
 * A domain rule that maps onto a specific form field. Controllers turn these
 * into BindingResult field errors so the user sees the problem next to the
 * input that caused it, rather than as an error page.
 */
public class FieldValidationException extends BusinessRuleException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
