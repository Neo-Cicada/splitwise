package com.splitwise.split;

import com.splitwise.support.BusinessRuleException;

/**
 * A split could not be computed from the given inputs. Extends
 * BusinessRuleException so controllers already turn it into a flash error
 * rather than a 500 page.
 */
public class InvalidSplitException extends BusinessRuleException {

    public InvalidSplitException(String message) {
        super(message);
    }
}
