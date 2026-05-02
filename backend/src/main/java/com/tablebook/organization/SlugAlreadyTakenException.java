package com.tablebook.organization;

import com.tablebook.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SlugAlreadyTakenException extends BusinessException {
    public SlugAlreadyTakenException(String slug) {
        super("Slug already taken: " + slug);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String getCode() {
        return "SLUG_ALREADY_TAKEN";
    }
}
