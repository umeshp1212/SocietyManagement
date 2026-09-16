package com.society.module.owner.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.society.enums.OwnerStatus;
import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.PermissionRepository;
import com.society.module.auth.repository.RoleRepository;
import com.society.module.auth.repository.UserRepository;
import com.society.module.owner.dto.RecipientScope;
import com.society.module.owner.dto.SendOwnerEmailRequest;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.repository.OwnerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc security integration tests for the Owner Email feature (Subtask 9.1).
 *
 * <p>Exercises the full request pipeline ({@code @SpringBootTest} + {@link MockMvc})
 * against a seeded in-memory H2 database, so the method-security guard
 * ({@code @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")})
 * on {@code POST /owners/email/send}, the security exception handling, and the shared
 * {@code ApiResponse} envelope are validated end-to-end. The controller is mounted
 * under the {@code /api} context path in production; MockMvc targets the mapping
 * directly.</p>
 *
 * <p>Cases covered:</p>
 * <ul>
 *   <li>unauthenticated request &rarr; 401 (Req 6.1)</li>
 *   <li>authenticated caller without {@code OWNER_EMAIL_SEND}/{@code SUPER_ADMIN}
 *       &rarr; 403 (Req 6.2)</li>
 *   <li>authenticated caller with {@code OWNER_EMAIL_SEND} &rarr; request processed
 *       (200 with a Send Report) (Req 6.3)</li>
 *   <li>{@code SUPER_ADMIN} caller &rarr; request processed (Req 6.3)</li>
 *   <li>each authentication/authorization rejection is recorded in the application
 *       audit log (Req 6.4)</li>
 * </ul>
 *
 * <p>Authentication is simulated with Spring Security test's {@code @WithMockUser}/
 * {@code @WithAnonymousUser}; the authorities drive the method-security guard. No mail
 * transport is configured in the test context, so a processed request degrades
 * gracefully and returns a Send Report with {@code mailConfigured == false} rather than
 * attempting a real send. That the endpoint returns {@code 200} (instead of 401/403) is
 * the observable proof that an authorized request was accepted for processing.</p>
 *
 * <p>Validates: Requirements 6.1, 6.2, 6.3, 6.4</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL-compatible) instead of the configured MySQL
        // datasource; Hibernate creates the schema from the entity mappings so the full
        // application context and the real security pipeline load without an external DB.
        "spring.datasource.url=jdbc:h2:mem:owner_email_security_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class OwnerEmailControllerSecurityIntegrationTest {

    private static final String ADMIN_USERNAME = "admin_oe_it";
    private static final String SENDER_USERNAME = "sender_oe_it";
    private static final String NO_ACCESS_USERNAME = "no_access_oe_it";

    private static final String OWNER_EMAIL_SEND = "OWNER_EMAIL_SEND";

    /**
     * Controller path as seen by {@link MockMvc}. The production
     * {@code server.servlet.context-path=/api} is a servlet-container setting MockMvc
     * does not apply, so requests target the controller mapping directly.
     */
    private static final String SEND_PATH = "/owners/email/send";

    /** Name of the dedicated audit logger the security layer writes rejections to. */
    private static final String AUDIT_LOGGER_NAME = "SECURITY_AUDIT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private OwnerRepository ownerRepository;

    private ListAppender<ILoggingEvent> auditAppender;
    private Logger auditLogger;

    @BeforeEach
    void seed() {
        // Clean slate so ordering/assertions are deterministic.
        userRepository.deleteAll();
        ownerRepository.deleteAll();

        // --- Permission + roles ---
        Permission ownerEmailSend = permissionRepository.findByPermissionName(OWNER_EMAIL_SEND)
                .orElseGet(() -> permissionRepository.save(Permission.builder()
                        .permissionName(OWNER_EMAIL_SEND)
                        .module("OWNER")
                        .description("Send templated emails to owners")
                        .build()));

        Role superAdminRole = roleRepository.findByRoleName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("SUPER_ADMIN")
                        .displayName("Super Admin")
                        .description("Full access")
                        .build()));

        Role senderRole = roleRepository.findByRoleName("EMAIL_SENDER_IT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("EMAIL_SENDER_IT")
                        .displayName("Email Sender")
                        .description("Holds OWNER_EMAIL_SEND")
                        .build()));
        if (senderRole.getPermissions().stream()
                .noneMatch(p -> OWNER_EMAIL_SEND.equals(p.getPermissionName()))) {
            senderRole.getPermissions().add(ownerEmailSend);
            senderRole = roleRepository.save(senderRole);
        }

        Role plainRole = roleRepository.findByRoleName("PLAIN_OE_IT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("PLAIN_OE_IT")
                        .displayName("Plain")
                        .description("No owner-email access")
                        .build()));

        // --- Users ---
        userRepository.save(User.builder()
                .username(ADMIN_USERNAME)
                .password("x")
                .fullName("Admin OE IT")
                .isActive(true)
                .roles(new HashSet<>(Set.of(superAdminRole)))
                .build());

        userRepository.save(User.builder()
                .username(SENDER_USERNAME)
                .password("x")
                .fullName("Sender OE IT")
                .isActive(true)
                .roles(new HashSet<>(Set.of(senderRole)))
                .build());

        userRepository.save(User.builder()
                .username(NO_ACCESS_USERNAME)
                .password("x")
                .fullName("No Access OE IT")
                .isActive(true)
                .roles(new HashSet<>(Set.of(plainRole)))
                .build());

        // One active owner with a valid email so an authorized ALL-scope send resolves a
        // recipient set (the send itself degrades gracefully since no mail is configured).
        ownerRepository.save(Owner.builder()
                .fullName("Active Owner OE")
                .contactNumber("9000000010")
                .email("active.owner.oe@example.com")
                .status(OwnerStatus.ACTIVE)
                .build());

        // Attach a Logback list appender to the dedicated audit logger so we can assert
        // that authentication/authorization rejections are recorded (Req 6.4).
        auditLogger = (Logger) LoggerFactory.getLogger(AUDIT_LOGGER_NAME);
        auditAppender = new ListAppender<>();
        auditAppender.start();
        auditLogger.addAppender(auditAppender);
        auditLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void detachAppender() {
        if (auditLogger != null && auditAppender != null) {
            auditLogger.detachAppender(auditAppender);
            auditAppender.stop();
        }
    }

    private SendOwnerEmailRequest validAllScopeRequest() {
        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Society Notice");
        request.setBody("Please note the upcoming general body meeting this weekend.");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ------------------------------------------------------------------
    // 6.1 Unauthenticated -> 401, and the rejection is audited (6.4)
    // ------------------------------------------------------------------

    @Test
    @WithAnonymousUser
    void sendWithoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validAllScopeRequest())))
                .andExpect(status().isUnauthorized());

        assertThat(auditMessages())
                .anySatisfy(msg -> assertThat(msg).contains("Authentication rejected"));
    }

    // ------------------------------------------------------------------
    // 6.2 Authenticated but lacks OWNER_EMAIL_SEND/SUPER_ADMIN -> 403, audited (6.4)
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = NO_ACCESS_USERNAME, authorities = {"ROLE_PLAIN_OE_IT"})
    void sendWithoutPermission_returns403() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validAllScopeRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        assertThat(auditMessages())
                .anySatisfy(msg -> assertThat(msg).contains("Authorization rejected"));
    }

    // ------------------------------------------------------------------
    // 6.3 Authenticated WITH OWNER_EMAIL_SEND -> request processed (200 + report)
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = SENDER_USERNAME, authorities = {OWNER_EMAIL_SEND})
    void sendWithPermission_isProcessed() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validAllScopeRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                // The active owner is resolved as an attempted recipient, proving the
                // request reached the service for processing.
                .andExpect(jsonPath("$.data.totalAttempted", is(1)));

        // An authorized request must not be recorded as a security rejection.
        assertThat(auditMessages()).noneSatisfy(msg ->
                assertThat(msg).containsAnyOf("Authentication rejected", "Authorization rejected"));
    }

    // ------------------------------------------------------------------
    // 6.3 SUPER_ADMIN -> request processed
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = ADMIN_USERNAME, authorities = {"ROLE_SUPER_ADMIN"})
    void sendAsSuperAdmin_isProcessed() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validAllScopeRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalAttempted", is(1)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private java.util.List<String> auditMessages() {
        return auditAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
