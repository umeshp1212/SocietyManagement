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

    /** Authority that gates reassigning a wrongly-attributed payment to the correct unit's bill. */
    private static final String MAINTENANCE_PAYMENT_REASSIGN = "MAINTENANCE_PAYMENT_REASSIGN";

    /** Authority that gates sending templated emails to owners (design: OWNER_EMAIL_SEND). */
    private static final String OWNER_EMAIL_SEND = "OWNER_EMAIL_SEND";

    /** Role for gate security guards who register incoming visitors/deliveries. */
    private static final String SECURITY_ROLE = "SECURITY";

    /** Authority that gates creating a visitor/delivery entry request from the gate. */
    private static final String VISITOR_CREATE = "VISITOR_CREATE";

    /** Authority that gates viewing visitor/delivery requests and the gate history/log. */
    private static final String VISITOR_VIEW = "VISITOR_VIEW";

    /** Authority that gates an owner/tenant approving or rejecting a visitor request. */
    private static final String VISITOR_RESPOND = "VISITOR_RESPOND";

    /** Committee/admin-facing roles that may send templated emails to owners. */
    private static final List<String> OWNER_EMAIL_ROLES = List.of(
            "CHAIRMAN", "SECRETARY", "COMMITTEE_MEMBER");

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
        seedPaymentReassignPermission();
        seedOwnerEmailSendPermission();
        seedVisitorGatePermissions();

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

    /**
     * Ensures the {@code MAINTENANCE_PAYMENT_REASSIGN} permission exists and is granted to the
     * society-wide management roles (chairman/secretary/treasurer). SUPER_ADMIN bypasses the
     * authority check via {@code hasRole('SUPER_ADMIN')}. Idempotent on every startup.
     */
    private void seedPaymentReassignPermission() {
        Permission reassign = permissionRepository.findByPermissionName(MAINTENANCE_PAYMENT_REASSIGN)
                .orElseGet(() -> {
                    Permission permission = Permission.builder()
                            .permissionName(MAINTENANCE_PAYMENT_REASSIGN)
                            .module("maintenance")
                            .description("Reassign a wrongly-attributed payment to the correct unit's bill")
                            .build();
                    log.info("Creating {} permission.", MAINTENANCE_PAYMENT_REASSIGN);
                    return permissionRepository.save(permission);
                });

        for (String roleName : SOCIETY_WIDE_ROLES) {
            Role role = roleRepository.findByRoleName(roleName)
                    .orElseGet(() -> {
                        Role newRole = Role.builder()
                                .roleName(roleName)
                                .displayName(toDisplayName(roleName))
                                .description("Auto-provisioned society-wide role")
                                .build();
                        log.info("Creating {} role.", roleName);
                        return roleRepository.save(newRole);
                    });

            if (role.getPermissions().stream()
                    .noneMatch(p -> MAINTENANCE_PAYMENT_REASSIGN.equals(p.getPermissionName()))) {
                role.getPermissions().add(reassign);
                roleRepository.save(role);
                log.info("Granted {} to role {}.", MAINTENANCE_PAYMENT_REASSIGN, roleName);
            }
        }
    }

    /**
     * Ensures the {@code OWNER_EMAIL_SEND} permission exists and is granted to the
     * committee/admin roles (chairman/secretary/committee member) that may send
     * templated emails to owners. SUPER_ADMIN bypasses the authority check via
     * {@code hasRole('SUPER_ADMIN')}. Idempotent on every startup, guaranteeing the
     * permission is present even when {@code data.sql} is not applied.
     */
    private void seedOwnerEmailSendPermission() {
        Permission ownerEmailSend = permissionRepository.findByPermissionName(OWNER_EMAIL_SEND)
                .orElseGet(() -> {
                    Permission permission = Permission.builder()
                            .permissionName(OWNER_EMAIL_SEND)
                            .module("OWNER")
                            .description("Send templated emails to owners")
                            .build();
                    log.info("Creating {} permission.", OWNER_EMAIL_SEND);
                    return permissionRepository.save(permission);
                });

        for (String roleName : OWNER_EMAIL_ROLES) {
            Role role = roleRepository.findByRoleName(roleName)
                    .orElseGet(() -> {
                        Role newRole = Role.builder()
                                .roleName(roleName)
                                .displayName(toDisplayName(roleName))
                                .description("Auto-provisioned committee/admin role")
                                .build();
                        log.info("Creating {} role.", roleName);
                        return roleRepository.save(newRole);
                    });

            if (role.getPermissions().stream()
                    .noneMatch(p -> OWNER_EMAIL_SEND.equals(p.getPermissionName()))) {
                role.getPermissions().add(ownerEmailSend);
                roleRepository.save(role);
                log.info("Granted {} to role {}.", OWNER_EMAIL_SEND, roleName);
            }
        }
    }

    /**
     * Seeds the visitor/delivery gate-approval authorities and ensures the roles that
     * need them exist. Mirrors the other seeding methods: idempotent on every startup,
     * so the SECURITY role and VISITOR_* permissions are present even when
     * {@code data.sql} is not applied.
     *
     * <p>Multiple guard users are not hardcoded here — admins create as many SECURITY
     * users as they need through the existing user-management flow. This method only
     * guarantees the SECURITY role is resolvable by name and carries the create/view
     * authorities, and that owner/tenant-facing roles can respond to requests.</p>
     */
    private void seedVisitorGatePermissions() {
        Permission visitorCreate = ensurePermission(VISITOR_CREATE, "visitor",
                "Register an incoming visitor/delivery entry request at the gate");
        Permission visitorView = ensurePermission(VISITOR_VIEW, "visitor",
                "View visitor/delivery requests and the gate history/log");
        Permission visitorRespond = ensurePermission(VISITOR_RESPOND, "visitor",
                "Approve or reject a visitor/delivery entry request");

        // Guards (SECURITY) can create and view requests.
        grantPermissionsToRole(SECURITY_ROLE, visitorCreate, visitorView);

        // Society-wide management roles can view the gate log for audit.
        for (String roleName : SOCIETY_WIDE_ROLES) {
            grantPermissionsToRole(roleName, visitorView);
        }

        // Members (owners/tenants) can respond to requests for their units.
        for (String roleName : MEMBER_ROLES) {
            grantPermissionsToRole(roleName, visitorRespond);
        }
    }

    /** Finds or creates a permission by name. Idempotent. */
    private Permission ensurePermission(String permissionName, String module, String description) {
        return permissionRepository.findByPermissionName(permissionName)
                .orElseGet(() -> {
                    Permission permission = Permission.builder()
                            .permissionName(permissionName)
                            .module(module)
                            .description(description)
                            .build();
                    log.info("Creating {} permission.", permissionName);
                    return permissionRepository.save(permission);
                });
    }

    /**
     * Ensures a role exists (auto-provisioning it by name if missing) and grants it the
     * given permissions, saving only when something changed. Idempotent.
     */
    private void grantPermissionsToRole(String roleName, Permission... permissions) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .roleName(roleName)
                            .displayName(toDisplayName(roleName))
                            .description("Auto-provisioned role")
                            .build();
                    log.info("Creating {} role.", roleName);
                    return roleRepository.save(newRole);
                });

        boolean changed = false;
        for (Permission permission : permissions) {
            boolean alreadyGranted = role.getPermissions().stream()
                    .anyMatch(p -> p.getPermissionName().equals(permission.getPermissionName()));
            if (!alreadyGranted) {
                role.getPermissions().add(permission);
                changed = true;
                log.info("Granted {} to role {}.", permission.getPermissionName(), roleName);
            }
        }
        if (changed) {
            roleRepository.save(role);
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
