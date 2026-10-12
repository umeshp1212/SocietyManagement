package com.society.module.visitor.controller;

import com.society.enums.OccupancyStatus;
import com.society.enums.OwnerStatus;
import com.society.enums.UnitType;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc integration tests for the gate (guard) endpoints (plan Task 3). Exercises the
 * full pipeline including the {@code @PreAuthorize("hasRole('SECURITY') ...")} method
 * guard and the create flow against a seeded in-memory H2 database.
 *
 * <p>Cases:
 * <ul>
 *   <li>unauthenticated create &rarr; 401</li>
 *   <li>authenticated without SECURITY/VISITOR_CREATE &rarr; 403</li>
 *   <li>SECURITY guard creates for a real flat &rarr; 201, PENDING, createdBy = guard</li>
 *   <li>SECURITY guard create for an unknown flat &rarr; clean error (not found)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gate_integration_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class GateControllerIntegrationTest {

    private static final String PATH = "/gate/visitor-requests";
    private static final String GUARD_USERNAME = "guard_it";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private UnitRepository unitRepository;
    @Autowired
    private UnitOwnerRepository unitOwnerRepository;
    @Autowired
    private com.society.module.visitor.repository.VisitorRequestRepository visitorRequestRepository;

    private Long unitId;

    @BeforeEach
    void seed() {
        // Delete children before parents to satisfy FK constraints. Visitor requests
        // created by prior test methods reference units, so clear them first.
        visitorRequestRepository.deleteAll();
        unitOwnerRepository.deleteAll();
        unitRepository.deleteAll();
        ownerRepository.deleteAll();

        Unit unit = unitRepository.save(Unit.builder()
                .unitNumber("A-101").wing("A").floor("1")
                .unitType(UnitType.FLAT)
                .monthlyMaintenanceAmount(BigDecimal.ZERO)
                .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                .status("ACTIVE")
                .build());
        this.unitId = unit.getUnitId();

        Owner owner = ownerRepository.save(Owner.builder()
                .fullName("Alice Owner").contactNumber("9000000001")
                .email("alice@example.com").status(OwnerStatus.ACTIVE)
                .build());

        unitOwnerRepository.save(UnitOwner.builder()
                .unit(unit).owner(owner).isPrimary(true)
                .ownershipPercentage(new BigDecimal("100.00"))
                .build());
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", "id.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    @WithAnonymousUser
    void unauthenticatedCreateIsUnauthorized() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .param("unitId", unitId.toString())
                        .param("visitorName", "Courier"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "someone", roles = {"OWNER"})
    void nonGuardCreateIsForbidden() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .param("unitId", unitId.toString())
                        .param("visitorName", "Courier"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = GUARD_USERNAME, roles = {"SECURITY"})
    void guardCreatesPendingRequest() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .file(photo())
                        .param("unitId", unitId.toString())
                        .param("visitorName", "Courier Guy")
                        .param("company", "Amazon"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.unitNumber", is("A-101")))
                .andExpect(jsonPath("$.data.visitorName", is("Courier Guy")))
                .andExpect(jsonPath("$.data.createdBy", is(GUARD_USERNAME)));
    }

    @Test
    @WithMockUser(username = GUARD_USERNAME, roles = {"SECURITY"})
    void guardCreateForUnknownFlatFails() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .param("unitId", "99999")
                        .param("visitorName", "Courier"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
