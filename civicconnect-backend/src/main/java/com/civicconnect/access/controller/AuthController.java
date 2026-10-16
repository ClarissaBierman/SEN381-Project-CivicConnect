package com.civicconnect.access.controller;

import com.civicconnect.access.dto.AuthResponse;
import com.civicconnect.access.dto.LoginRequest;
import com.civicconnect.access.dto.MeResponse;
import com.civicconnect.access.dto.RegisterRequest;
import com.civicconnect.access.dto.RegistrationResponse;
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

    /** 202: the same answer whether or not the email address was already registered (D-25). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest body) {
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
