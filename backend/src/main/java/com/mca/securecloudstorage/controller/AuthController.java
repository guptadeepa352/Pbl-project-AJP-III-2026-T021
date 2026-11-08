package com.mca.securecloudstorage.controller;

import com.mca.securecloudstorage.dto.LoginRequest;
import com.mca.securecloudstorage.dto.LoginResponse;
import com.mca.securecloudstorage.dto.CurrentUserResponse;
import com.mca.securecloudstorage.dto.RegisterRequest;
import com.mca.securecloudstorage.dto.RegistrationResponse;
import com.mca.securecloudstorage.entity.User;
import com.mca.securecloudstorage.security.JwtService;
import com.mca.securecloudstorage.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(
            UserService userService,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        User user = userService.createUser(
                request.name(),
                request.email(),
                request.password());
        RegistrationResponse response = new RegistrationResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.email().trim().toLowerCase(Locale.ROOT),
                        request.password()));

        String token = jwtService.generateToken(authentication.getName());
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds());
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(@AuthenticationPrincipal UserDetails userDetails) {
        return new CurrentUserResponse(userDetails.getUsername());
    }
}
