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
import java.util.Optional;
import java.util.Set;

/**
 * Creates default admin user on first startup if no users exist.
 * Also ensures admin user password is reset if admin account exists but cannot login.
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
        Optional<User> existingAdmin = userRepository.findByUsername("admin");

        if (existingAdmin.isPresent()) {
            // Ensure admin is active and has SUPER_ADMIN role
            User admin = existingAdmin.get();
            boolean updated = false;

            // Reset admin password to default if it doesn't match
            if (!passwordEncoder.matches("Admin@123", admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode("Admin@123"));
                updated = true;
                log.info("Admin password reset to default: Admin@123");
            }

            if (!admin.getIsActive()) {
                admin.setIsActive(true);
                updated = true;
            }

            // Ensure SUPER_ADMIN role is assigned
            Role superAdminRole = roleRepository.findByRoleName("SUPER_ADMIN").orElse(null);
            if (superAdminRole != null && !admin.getRoles().contains(superAdminRole)) {
                admin.getRoles().add(superAdminRole);
                updated = true;
            }

            if (updated) {
                userRepository.save(admin);
                log.info("Admin user updated (ensured active + SUPER_ADMIN role).");
            }

            log.info("Admin user exists. Username: admin");
        } else {
            log.info("No admin user found. Creating default admin user...");

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
