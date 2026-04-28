package com.tablebook.auth;

import com.tablebook.auth.dto.CreateUserRequest;
import com.tablebook.auth.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        return toResponse(user);
    }

    public UserResponse create(CreateUserRequest request) {
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
