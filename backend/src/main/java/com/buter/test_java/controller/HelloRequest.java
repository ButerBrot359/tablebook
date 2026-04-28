package com.buter.test_java.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HelloRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
        String name
) {}

