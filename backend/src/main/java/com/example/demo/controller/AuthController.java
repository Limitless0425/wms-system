package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepo;

    public AuthController(JwtUtil jwtUtil, PasswordEncoder passwordEncoder, UserRepository userRepo) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.userRepo = userRepo;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        User user = userRepo.findByUsername(request.getUsername()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Map.of("code", 401, "message", "用户名或密码错误，或账号已停用");
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return Map.of("code", 200, "message", "登录成功",
                "data", new LoginResponse(token, user.getUsername(), user.getRole()));
    }

    @GetMapping("/userinfo")
    public Map<String, Object> userinfo(@RequestHeader("Authorization") String authHeader) {
        var claims = jwtUtil.parseToken(authHeader.substring(7));
        User user = userRepo.findByUsername(claims.getSubject()).orElse(null);
        return Map.of("code", 200, "data", Map.of(
                "username", claims.getSubject(),
                "displayName", user == null || user.getDisplayName() == null ? claims.getSubject() : user.getDisplayName(),
                "role", claims.get("role", String.class)
        ));
    }
}
