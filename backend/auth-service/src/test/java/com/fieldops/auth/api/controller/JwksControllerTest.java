package com.fieldops.auth.api.controller;

import com.fieldops.auth.infrastructure.config.JwtProperties;
import com.fieldops.auth.infrastructure.config.RsaKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwksControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setKeyPath(tempDir.toString());
        props.setKeyId("test-jwks-kid");
        props.setAllowKeyGeneration(true);

        RsaKeyProvider provider = new RsaKeyProvider(props);
        provider.afterPropertiesSet();

        JwksController controller = new JwksController(provider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getJwksReturnsValidJwkSet() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value("test-jwks-kid"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"));
    }
}
