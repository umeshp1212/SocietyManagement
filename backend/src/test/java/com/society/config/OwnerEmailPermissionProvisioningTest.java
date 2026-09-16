package com.society.config;

import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.repository.PermissionRepository;
import com.society.module.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup provisioning test for the {@code OWNER_EMAIL_SEND} permission (Subtask 9.2).
 *
 * <p>Boots the full application context against a fresh in-memory H2 database so the
 * {@link DataInitializer} {@code CommandLineRunner} runs its idempotent
 * {@code seedOwnerEmailSendPermission()} exactly as it would on a real startup. The
 * test then asserts, directly against the persistence layer, that:
 * <ul>
 *   <li>the {@code OWNER_EMAIL_SEND} permission exists in module {@code OWNER}; and</li>
 *   <li>it is granted to each committee/admin role the design designates
 *       (CHAIRMAN, SECRETARY, COMMITTEE_MEMBER); and</li>
 *   <li>SUPER_ADMIN — which the design covers via the {@code data.sql} grant-all and
 *       via {@code hasRole('SUPER_ADMIN')} — holds it once the grant-all is applied.</li>
 * </ul>
 *
 * <p>The production {@code db/data.sql} is MySQL-specific and is skipped here
 * ({@code spring.sql.init.mode=never}); this isolates the assertions to the
 * {@link DataInitializer} provisioning path, which is the authoritative guarantee that
 * the permission and its committee/admin grants exist even where {@code data.sql} is
 * not applied. The SUPER_ADMIN "grant-all" is reproduced in-test (it is otherwise a
 * pure {@code data.sql} concern) and idempotency is exercised by re-running the
 * initializer and confirming no duplicate permission or grant appears.
 *
 * <p>Validates: Requirements 6.3
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL-compatible); Hibernate builds the schema from the
        // entity mappings so the full context (and DataInitializer) starts without MySQL.
        "spring.datasource.url=jdbc:h2:mem:owner_email_perm_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // The production db/data.sql is MySQL-specific; skip SQL script init for this test.
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class OwnerEmailPermissionProvisioningTest {

    private static final String OWNER_EMAIL_SEND = "OWNER_EMAIL_SEND";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    @Autowired
    private DataInitializer dataInitializer;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private RoleRepository roleRepository;

    // ------------------------------------------------------------------
    // The permission itself is created by DataInitializer at startup.
    // ------------------------------------------------------------------

    @Test
    void ownerEmailSendPermissionExistsInOwnerModule() {
        Permission permission = permissionRepository.findByPermissionName(OWNER_EMAIL_SEND)
                .orElseThrow(() -> new AssertionError(
                        "DataInitializer must create the " + OWNER_EMAIL_SEND + " permission"));

        assertThat(permission.getModule()).isEqualTo("OWNER");
    }

    // ------------------------------------------------------------------
    // Committee/admin roles are granted the permission by DataInitializer.
    // ------------------------------------------------------------------

    @Test
    void chairmanRoleIsGrantedOwnerEmailSend() {
        assertRoleHasOwnerEmailSend("CHAIRMAN");
    }

    @Test
    void secretaryRoleIsGrantedOwnerEmailSend() {
        assertRoleHasOwnerEmailSend("SECRETARY");
    }

    @Test
    void committeeMemberRoleIsGrantedOwnerEmailSend() {
        assertRoleHasOwnerEmailSend("COMMITTEE_MEMBER");
    }

    // ------------------------------------------------------------------
    // SUPER_ADMIN is covered by the data.sql grant-all (reproduced here since
    // data.sql is skipped under H2) in addition to hasRole('SUPER_ADMIN').
    // ------------------------------------------------------------------

    @Test
    void superAdminRoleIsGrantedOwnerEmailSendViaGrantAll() {
        Role superAdmin = roleRepository.findByRoleName(SUPER_ADMIN)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName(SUPER_ADMIN)
                        .displayName("Super Admin")
                        .description("Full access to all modules")
                        .build()));

        // Reproduce data.sql's "SUPER_ADMIN gets ALL permissions" grant-all: give the
        // role every permission that currently exists (which includes OWNER_EMAIL_SEND
        // provisioned by DataInitializer at startup).
        superAdmin.getPermissions().addAll(permissionRepository.findAll());
        roleRepository.save(superAdmin);

        Role reloaded = roleRepository.findByRoleName(SUPER_ADMIN).orElseThrow();
        assertThat(reloaded.getPermissions())
                .extracting(Permission::getPermissionName)
                .contains(OWNER_EMAIL_SEND);
    }

    // ------------------------------------------------------------------
    // Idempotency: re-running the initializer neither duplicates the permission
    // nor re-adds the grant to an already-granted role.
    // ------------------------------------------------------------------

    @Test
    void reRunningInitializerIsIdempotent() {
        long permissionsBefore = permissionRepository.findByModule("OWNER").stream()
                .filter(p -> OWNER_EMAIL_SEND.equals(p.getPermissionName()))
                .count();
        assertThat(permissionsBefore).isEqualTo(1);

        // Run the seeding a second time; it must not create a duplicate permission
        // nor a duplicate grant on the committee roles.
        dataInitializer.run();

        long permissionsAfter = permissionRepository.findByModule("OWNER").stream()
                .filter(p -> OWNER_EMAIL_SEND.equals(p.getPermissionName()))
                .count();
        assertThat(permissionsAfter).isEqualTo(1);

        Role chairman = roleRepository.findByRoleName("CHAIRMAN").orElseThrow();
        long grants = chairman.getPermissions().stream()
                .filter(p -> OWNER_EMAIL_SEND.equals(p.getPermissionName()))
                .count();
        assertThat(grants).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private void assertRoleHasOwnerEmailSend(String roleName) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new AssertionError(
                        "DataInitializer must provision the " + roleName + " role"));

        assertThat(role.getPermissions())
                .as("%s should be granted %s", roleName, OWNER_EMAIL_SEND)
                .extracting(Permission::getPermissionName)
                .contains(OWNER_EMAIL_SEND);
    }
}
