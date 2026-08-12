package com.society.config;

import com.society.module.auth.entity.Role;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.RoleRepository;
import com.society.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Creates default admin user on first startup if no users exist.
 * Default credentials: admin / Admin@123
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("No users found. Creating default admin user...");

            // Ensure SUPER_ADMIN role exists (should be created by data.sql)
            Role superAdminRole = roleRepository.findByRoleName("SUPER_ADMIN")
                    .orElseGet(() -> {
                        Role role = Role.builder()
                                .roleName("SUPER_ADMIN")
                                .displayName("Super Admin")
                                .description("Full access to all modules")
                                .build();
                        return roleRepository.save(role);
                    });

            Set<Role> roles = new HashSet<>();
            roles.add(superAdminRole);

            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("Admin@123"))
                    .fullName("System Administrator")
                    .email("admin@society.com")
                    .phone("9999999999")
                    .isActive(true)
                    .roles(roles)
                    .build();

            userRepository.save(admin);
            log.info("Default admin user created. Username: admin, Password: Admin@123");
            log.info("*** PLEASE CHANGE THE DEFAULT PASSWORD AFTER FIRST LOGIN ***");
        }
    }
}
