package com.tablebook.auth.login;

import com.tablebook.auth.login.dto.LoginRequest;
import com.tablebook.auth.login.dto.LoginResponse;
import com.tablebook.auth.login.dto.RegisterRequest;
import com.tablebook.auth.security.JwtService;
import com.tablebook.auth.user.EmailAlreadyInUseException;
import com.tablebook.auth.user.User;
import com.tablebook.auth.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateAccessToken(user);
        return LoginResponse.bearer(token);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException(request.email());
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setPhone(request.phone());

        User saved = userRepository.save(user);

        String token = jwtService.generateAccessToken(saved);
        return LoginResponse.bearer(token);
    }
}
