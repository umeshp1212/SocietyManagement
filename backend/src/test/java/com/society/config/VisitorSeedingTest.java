package com.society.config;

import com.society.module.auth.dto.UserCreateRequest;
import com.society.module.auth.dto.UserDTO;
import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.repository.PermissionRepository;
import com.society.module.auth.repository.RoleRepository;
import com.society.module.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the visitor/gate seeding in {@link DataInitializer} (plan Task 1): the
 * SECURITY role and VISITOR_* permissions exist after startup, re-running the seeder is
 * idempotent (no duplicates), and multiple SECURITY guard users can be created via the
 * existing admin user-management flow, each carrying the SECURITY role.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:visitor_seed_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class VisitorSeedingTest {

    @Autowired
    private DataInitializer dataInitializer;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private UserService userService;

    @Test
    void securityRoleAndVisitorPermissionsAreSeeded() {
        assertThat(roleRepository.findByRoleName("SECURITY")).isPresent();

        assertThat(permissionRepository.findByPermissionName("VISITOR_CREATE")).isPresent();
        assertThat(permissionRepository.findByPermissionName("VISITOR_VIEW")).isPresent();
        assertThat(permissionRepository.findByPermissionName("VISITOR_RESPOND")).isPresent();

        Role security = roleRepository.findByRoleName("SECURITY").orElseThrow();
        assertThat(security.getPermissions())
                .extracting(Permission::getPermissionName)
                .contains("VISITOR_CREATE", "VISITOR_VIEW");
    }

    @Test
    void seedingIsIdempotent() {
        long rolesBefore = roleRepository.count();
        long permsBefore = permissionRepository.count();

        // Re-run the seeder; it must not create duplicate roles/permissions.
        dataInitializer.run();

        assertThat(roleRepository.count()).isEqualTo(rolesBefore);
        assertThat(permissionRepository.count()).isEqualTo(permsBefore);
    }

    @Test
    void multipleSecurityGuardUsersCanBeCreated() {
        UserCreateRequest g1 = new UserCreateRequest();
        g1.setUsername("guard_one");
        g1.setPassword("Guard@123");
        g1.setFullName("Guard One");
        g1.setRoles(List.of("SECURITY"));

        UserCreateRequest g2 = new UserCreateRequest();
        g2.setUsername("guard_two");
        g2.setPassword("Guard@123");
        g2.setFullName("Guard Two");
        g2.setRoles(List.of("SECURITY"));

        UserDTO created1 = userService.createUser(g1);
        UserDTO created2 = userService.createUser(g2);

        assertThat(created1.getRoles()).contains("SECURITY");
        assertThat(created2.getRoles()).contains("SECURITY");
        assertThat(created1.getUserId()).isNotEqualTo(created2.getUserId());
    }
}
