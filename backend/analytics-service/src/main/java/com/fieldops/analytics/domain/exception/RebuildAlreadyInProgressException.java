package com.fieldops.analytics.domain.exception;

public class RebuildAlreadyInProgressException extends RuntimeException {

    public RebuildAlreadyInProgressException(String message) {
        super(message);
    }
}
