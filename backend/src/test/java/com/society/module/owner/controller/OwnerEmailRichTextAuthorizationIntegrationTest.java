package com.society.module.owner.controller;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization integration test for the Owner Email Rich Text feature (Subtask 7.4).
 *
 * <p>Exercises the full request pipeline ({@code @SpringBootTest} + {@link MockMvc})
 * against a seeded in-memory H2 database so the method-security guard
 * ({@code @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")})
 * on {@code POST /owners/email/send} is validated end-to-end for a rich-text (HTML)
 * body. The rich-text change preserves the existing authorization behavior: the body
 * now carries HTML, but the same authentication/authorization rules apply
 * (Req 5.5).</p>
 *
 * <p>Cases covered:</p>
 * <ul>
 *   <li>unauthenticated request &rarr; 401, no send initiated (5.5)</li>
 *   <li>authenticated caller without {@code OWNER_EMAIL_SEND}/{@code SUPER_ADMIN}
 *       &rarr; 403, no send initiated (5.5)</li>
 *   <li>authenticated caller with {@code OWNER_EMAIL_SEND} &rarr; request processed
 *       (200 with a Send Report) (5.5)</li>
 * </ul>
 *
 * <p>Authentication is simulated with Spring Security test's {@code @WithMockUser}/
 * {@code @WithAnonymousUser}; the authorities drive the method-security guard. No mail
 * transport is configured in the test context, so an authorized request degrades
 * gracefully and returns a Send Report rather than attempting a real send. That the
 * endpoint returns {@code 200} (instead of 401/403) is the observable proof that an
 * authorized rich-text request was accepted for processing.</p>
 *
 * <p>Validates: Requirements 5.5</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        // Isolated in-memory H2 (MySQL-compatible) instead of the configured MySQL
        // datasource; Hibernate creates the schema from the entity mappings so the full
        // application context and the real security pipeline load without an external DB.
        "spring.datasource.url=jdbc:h2:mem:owner_email_rt_authz_it;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class OwnerEmailRichTextAuthorizationIntegrationTest {

    private static final String SENDER_USERNAME = "sender_oe_rt_it";
    private static final String NO_ACCESS_USERNAME = "no_access_oe_rt_it";

    private static final String OWNER_EMAIL_SEND = "OWNER_EMAIL_SEND";

    /**
     * Controller path as seen by {@link MockMvc}. The production
     * {@code server.servlet.context-path=/api} is a servlet-container setting MockMvc
     * does not apply, so requests target the controller mapping directly.
     */
    private static final String SEND_PATH = "/owners/email/send";

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

        Role senderRole = roleRepository.findByRoleName("EMAIL_SENDER_RT_IT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("EMAIL_SENDER_RT_IT")
                        .displayName("Email Sender")
                        .description("Holds OWNER_EMAIL_SEND")
                        .build()));
        if (senderRole.getPermissions().stream()
                .noneMatch(p -> OWNER_EMAIL_SEND.equals(p.getPermissionName()))) {
            senderRole.getPermissions().add(ownerEmailSend);
            senderRole = roleRepository.save(senderRole);
        }

        Role plainRole = roleRepository.findByRoleName("PLAIN_OE_RT_IT")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName("PLAIN_OE_RT_IT")
                        .displayName("Plain")
                        .description("No owner-email access")
                        .build()));

        // --- Users ---
        userRepository.save(User.builder()
                .username(SENDER_USERNAME)
                .password("x")
                .fullName("Sender OE RT IT")
                .isActive(true)
                .roles(new HashSet<>(Set.of(senderRole)))
                .build());

        userRepository.save(User.builder()
                .username(NO_ACCESS_USERNAME)
                .password("x")
                .fullName("No Access OE RT IT")
                .isActive(true)
                .roles(new HashSet<>(Set.of(plainRole)))
                .build());

        // One active owner with a valid email so an authorized ALL-scope send resolves a
        // recipient set (the send itself degrades gracefully since no mail is configured).
        ownerRepository.save(Owner.builder()
                .fullName("Active Owner OE RT")
                .contactNumber("9000000020")
                .email("active.owner.oe.rt@example.com")
                .status(OwnerStatus.ACTIVE)
                .build());
    }

    /**
     * A valid ALL-scope request whose body is rich-text HTML (bold, list, link) —
     * exactly what the rich-text editor now produces. The authorization guard is
     * indifferent to the body markup; this proves the rich-text change preserves it.
     */
    private SendOwnerEmailRequest validRichTextRequest() {
        SendOwnerEmailRequest request = new SendOwnerEmailRequest();
        request.setRecipientScope(RecipientScope.ALL);
        request.setSubject("Society Notice");
        request.setBody("<p><strong>Reminder:</strong> the general body meeting is "
                + "<em>this weekend</em>.</p><ul><li>Agenda review</li>"
                + "<li>Budget vote</li></ul>"
                + "<p>Details: <a href=\"https://example.com/agenda\">agenda</a></p>");
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ------------------------------------------------------------------
    // 5.5 Unauthenticated -> 401, no send initiated
    // ------------------------------------------------------------------

    @Test
    @WithAnonymousUser
    void sendWithoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRichTextRequest())))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 5.5 Authenticated but lacks OWNER_EMAIL_SEND/SUPER_ADMIN -> 403, no send initiated
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = NO_ACCESS_USERNAME, authorities = {"ROLE_PLAIN_OE_RT_IT"})
    void sendWithoutPermission_returns403() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRichTextRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ------------------------------------------------------------------
    // 5.5 Authenticated WITH OWNER_EMAIL_SEND -> request processed (200 + report)
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = SENDER_USERNAME, authorities = {OWNER_EMAIL_SEND})
    void sendWithPermission_isProcessed() throws Exception {
        mockMvc.perform(post(SEND_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRichTextRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                // The active owner is resolved as an attempted recipient, proving the
                // authorized rich-text request reached the service for processing.
                .andExpect(jsonPath("$.data.totalAttempted", is(1)));
    }
}
