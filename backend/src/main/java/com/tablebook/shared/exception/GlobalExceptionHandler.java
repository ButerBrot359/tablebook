package com.tablebook.shared.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public record ErrorResponse(
            OffsetDateTime timestamp,
            int status,
            String code,
            String message
    ) {}

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
