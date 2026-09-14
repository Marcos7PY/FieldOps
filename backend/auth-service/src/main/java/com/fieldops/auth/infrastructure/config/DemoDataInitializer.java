package com.fieldops.auth.infrastructure.config;

import com.fieldops.auth.domain.model.Role;
import com.fieldops.auth.domain.model.User;
import com.fieldops.auth.infrastructure.persistence.RoleRepository;
import com.fieldops.auth.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "fieldops.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.warn("DATOS DE DEMOSTRACIÓN ACTIVOS: se han creado usuarios con contraseña conocida. "
                + "No habilitar fieldops.demo-data.enabled en entornos productivos.");

        Role supervisorRole = roleRepository.findByName("ROLE_SUPERVISOR")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_SUPERVISOR")));
        Role technicianRole = roleRepository.findByName("ROLE_TECHNICIAN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_TECHNICIAN")));

        String passwordHash = passwordEncoder.encode("Demo2026!");

        if (!userRepository.existsByUsername("supervisor")) {
            User supervisor = new User();
            supervisor.setUsername("supervisor");
            supervisor.setPasswordHash(passwordHash);
            supervisor.setFullName("Supervisor General");
            supervisor.setEmail("supervisor@fieldops.com");
            supervisor.setActive(true);
            supervisor.setCreatedAt(LocalDateTime.now());
            supervisor.setRoles(Set.of(supervisorRole));
            userRepository.save(supervisor);
        }

        createUserIfAbsent("tecnico1", "Carlos Tecnico 1", "tecnico1@fieldops.com", passwordHash, technicianRole);
        createUserIfAbsent("tecnico2", "Ana Tecnico 2", "tecnico2@fieldops.com", passwordHash, technicianRole);
        createUserIfAbsent("tecnico3", "Luis Tecnico 3", "tecnico3@fieldops.com", passwordHash, technicianRole);
    }

    private void createUserIfAbsent(String username, String fullName, String email, String passwordHash, Role role) {
        if (!userRepository.existsByUsername(username)) {
            User tech = new User();
            tech.setUsername(username);
            tech.setPasswordHash(passwordHash);
            tech.setFullName(fullName);
            tech.setEmail(email);
            tech.setActive(true);
            tech.setCreatedAt(LocalDateTime.now());
            tech.setRoles(Set.of(role));
            userRepository.save(tech);
        }
    }
}
