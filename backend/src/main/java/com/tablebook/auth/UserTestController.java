package com.tablebook.auth;

import com.tablebook.auth.dto.CreateUserRequest;
import com.tablebook.auth.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/test/users")
@RequiredArgsConstructor
public class UserTestController {

    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping("{id}/token")
    public TokenResponse generateToken(@PathVariable Long id) {
        User user = userService.findUserById(id);
        String token = jwtService.generateAccessToken(user);

        return new TokenResponse(token);
    }

    public record TokenResponse(String token) {}

}
