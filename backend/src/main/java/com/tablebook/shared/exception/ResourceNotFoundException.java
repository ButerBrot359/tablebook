package com.tablebook.shared.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException{
    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " not found " + id);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String getCode() {
        return "RESOURCE_NOT_FOUND";
    }
}
