package com.society.config;

import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.PermissionRepository;
import com.society.module.auth.repository.RoleRepository;
import com.society.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
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

    /** Authority that gates access to the Transaction Page (design: TRANSACTION_VIEW). */
    private static final String TRANSACTION_VIEW = "TRANSACTION_VIEW";

    /**
     * Society-wide roles that view all transactions. They also receive the
     * {@code TRANSACTION_VIEW} permission so the endpoint's authority check
     * ({@code hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')}) is
     * satisfied uniformly; their society-wide scope is decided in the service.
     */
    private static final List<String> SOCIETY_WIDE_ROLES = List.of(
            "SUPER_ADMIN", "CHAIRMAN", "SECRETARY", "TREASURER");

    /** Member-facing roles that view only their own units' transactions. */
    private static final List<String> MEMBER_ROLES = List.of("OWNER", "TENANT");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedTransactionViewPermission();

        Optional<User> existingAdmin = userRepository.findByUsername("admin");

        if (existingAdmin.isPresent()) {
            // Ensure admin is active and has SUPER_ADMIN role
            User admin = existingAdmin.get();
            boolean updated = false;

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

    /**
     * Ensures the {@code TRANSACTION_VIEW} permission exists and is granted to the
     * member-facing and society-wide roles that need Transaction Page access.
     * Society-wide roles additionally receive society-wide scope, which is decided
     * in the service via the access-scope resolver. Idempotent on every startup.
     */
    private void seedTransactionViewPermission() {
        Permission transactionView = permissionRepository.findByPermissionName(TRANSACTION_VIEW)
                .orElseGet(() -> {
                    Permission permission = Permission.builder()
                            .permissionName(TRANSACTION_VIEW)
                            .module("transaction")
                            .description("View maintenance transactions on the Transaction Page")
                            .build();
                    log.info("Creating {} permission.", TRANSACTION_VIEW);
                    return permissionRepository.save(permission);
                });

        Set<String> rolesNeedingPermission = new HashSet<>();
        rolesNeedingPermission.addAll(SOCIETY_WIDE_ROLES);
        rolesNeedingPermission.addAll(MEMBER_ROLES);

        for (String roleName : rolesNeedingPermission) {
            Role role = roleRepository.findByRoleName(roleName)
                    .orElseGet(() -> {
                        Role newRole = Role.builder()
                                .roleName(roleName)
                                .displayName(toDisplayName(roleName))
                                .description("Auto-provisioned role for Transaction Page access")
                                .build();
                        log.info("Creating {} role.", roleName);
                        return roleRepository.save(newRole);
                    });

            if (role.getPermissions().stream()
                    .noneMatch(p -> TRANSACTION_VIEW.equals(p.getPermissionName()))) {
                role.getPermissions().add(transactionView);
                roleRepository.save(role);
                log.info("Granted {} to role {}.", TRANSACTION_VIEW, roleName);
            }
        }
    }

    private String toDisplayName(String roleName) {
        String[] parts = roleName.toLowerCase().split("_");
        StringBuilder display = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (display.length() > 0) {
                display.append(' ');
            }
            display.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return display.toString();
    }
}
