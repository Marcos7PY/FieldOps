package com.fieldops.auth.infrastructure.config;

import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.persistence.RoleRepository;
import com.fieldops.auth.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DemoDataInitializerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DemoDataInitializer demoDataInitializer;

    @Test
    void seedsFourDemoUsersWithCorrectRolesAndPassword() {
        Optional<User> supervisor = userRepository.findByUsername("supervisor");
        assertThat(supervisor).isPresent();
        assertThat(supervisor.get().getRoles()).anyMatch(r -> "ROLE_SUPERVISOR".equals(r.getName()));
        assertThat(passwordEncoder.matches("Demo2026!", supervisor.get().getPasswordHash())).isTrue();

        for (String tech : new String[]{"tecnico1", "tecnico2", "tecnico3"}) {
            Optional<User> u = userRepository.findByUsername(tech);
            assertThat(u).isPresent();
            assertThat(u.get().getRoles()).anyMatch(r -> "ROLE_TECHNICIAN".equals(r.getName()));
            assertThat(passwordEncoder.matches("Demo2026!", u.get().getPasswordHash())).isTrue();
        }

        long initialUserCount = userRepository.count();
        demoDataInitializer.run(null);
        assertThat(userRepository.count()).isEqualTo(initialUserCount);
    }
}