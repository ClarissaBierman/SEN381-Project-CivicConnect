package com.civicconnect.access.controller;

import com.civicconnect.access.dto.AuthResponse;
import com.civicconnect.access.dto.ClientResponse;
import com.civicconnect.access.dto.LoginRequest;
import com.civicconnect.access.dto.MeResponse;
import com.civicconnect.access.dto.RegisterRequest;
import com.civicconnect.access.service.AuthService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse register(@Valid @RequestBody RegisterRequest body) {
        return authService.register(body);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest body) {
        return authService.login(body);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }
}
