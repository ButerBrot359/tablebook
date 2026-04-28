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
    private final UserRepository userRepository;

    @GetMapping
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return toResponse(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash("FAKE_HASH_" + request.password());
        user.setFullName(request.fullName());
        user.setPhone(request.phone());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getPlatformRole(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
