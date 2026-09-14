package com.fieldops.auth.api.controller;

import com.fieldops.auth.application.dto.UserResponse;
import com.fieldops.auth.application.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_SUPERVISOR') or hasAuthority('ROLE_INTERNAL_SERVICE')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(authService.getUserById(id));
    }
}
