package com.emf.controlplane.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class GovernorLimitExceededException extends RuntimeException {

    public GovernorLimitExceededException(String message) {
        super(message);
    }
}
