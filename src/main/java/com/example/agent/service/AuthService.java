package com.example.agent.service;

import com.example.agent.model.dto.AuthRequest;
import com.example.agent.model.dto.AuthResponse;
import com.example.agent.model.entity.User;
import com.example.agent.exception.BusinessException;
import com.example.agent.repository.UserRepository;
import com.example.agent.security.JwtTokenProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
            user.getUsername(), user.getPassword(), java.util.Collections.emptyList()
        );
        String token = jwtTokenProvider.generateToken(userDetails);

        return new AuthResponse(token, user.getUsername(), user.getId());
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
            user.getUsername(), user.getPassword(), java.util.Collections.emptyList()
        );
        String token = jwtTokenProvider.generateToken(userDetails);

        return new AuthResponse(token, user.getUsername(), user.getId());
    }
}
