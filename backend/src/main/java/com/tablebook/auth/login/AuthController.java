package com.tablebook.auth.login;

import com.tablebook.auth.login.dto.LoginRequest;
import com.tablebook.auth.login.dto.LoginResponse;
import com.tablebook.auth.login.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}
