package com.fieldops.auth.api.controller;

import com.fieldops.auth.application.dto.UserResponse;
import com.fieldops.auth.application.service.AuthService;
import com.fieldops.auth.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getUserByIdReturnsUserWhenFound() throws Exception {
        UserResponse response = new UserResponse(
                42L,
                "tecnico2",
                "Carlos Gomez",
                "otro@ejemplo.com",
                List.of("ROLE_TECHNICIAN")
        );
        when(authService.getUserById(42L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/42")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("tecnico2"))
                .andExpect(jsonPath("$.fullName").value("Carlos Gomez"))
                .andExpect(jsonPath("$.email").value("otro@ejemplo.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_TECHNICIAN"));
    }

    @Test
    void getUserByIdReturnsNotFoundWhenMissing() throws Exception {
        when(authService.getUserById(999L)).thenThrow(new UserNotFoundException("User not found: 999"));

        mockMvc.perform(get("/api/v1/users/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
