package com.fieldops.auth.infrastructure.config;

import com.fieldops.auth.AbstractIntegrationTest;
import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoDataInitializerTest {

    @Nested
    @ActiveProfiles("test")
    @TestPropertySource(properties = "fieldops.demo-data.enabled=true")
    class WhenDemoDataEnabled extends AbstractIntegrationTest {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private DemoDataInitializer demoDataInitializer;

        @Test
        @DisplayName("Crea los 4 usuarios de demostración y el bean existe")
        void seedsFourDemoUsersWithCorrectRolesAndPassword() {
            assertThat(demoDataInitializer).isNotNull();

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

    @Nested
    @ActiveProfiles("test")
    @TestPropertySource(properties = "fieldops.demo-data.enabled=false")
    class WhenDemoDataDisabled extends AbstractIntegrationTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("El bean DemoDataInitializer no se crea cuando la propiedad está a false o ausente")
        void beanShouldNotExistWhenDisabled() {
            assertThrows(NoSuchBeanDefinitionException.class, () -> context.getBean(DemoDataInitializer.class));
        }
    }
}
