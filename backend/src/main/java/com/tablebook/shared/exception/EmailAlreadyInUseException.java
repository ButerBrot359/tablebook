package com.tablebook.shared.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyInUseException extends BusinessException{

    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String getCode() {
        return "EMAIL_ALREADY_IN_USE";
    }
}
