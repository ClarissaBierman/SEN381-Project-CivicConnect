package com.civicconnect.controller;

import com.civicconnect.dto.AuthResponse;
import com.civicconnect.dto.ClientResponse;
import com.civicconnect.dto.LoginRequest;
import com.civicconnect.dto.MeResponse;
import com.civicconnect.dto.RegisterRequest;
import com.civicconnect.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** API-01 to API-03. Register and login are the only public endpoints. */
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
