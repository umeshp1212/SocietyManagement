package com.society.module.visitor.repository;

import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
import com.society.enums.VisitorStatus;
import com.society.module.owner.entity.Unit;
import com.society.module.visitor.entity.VisitorRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence tests for {@link VisitorRequestRepository} (plan Task 2): verifies the
 * enum is persisted/read back correctly, the status finder returns matching rows, and
 * the expired-pending query selects only PENDING rows whose {@code expiresAt} is in the
 * past — the exact set the auto-expiry scheduler (Task 5) acts on.
 *
 * <p>Runs against an isolated in-memory H2 database (MySQL mode) with the schema created
 * from the entity mappings; the MySQL-specific {@code db/data.sql} init is skipped.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:visitor_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF"
})
class VisitorRequestRepositoryTest {

    @Autowired
    private VisitorRequestRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Unit persistUnit(String unitNumber) {
        Unit unit = Unit.builder()
                .unitNumber(unitNumber)
                .wing("A")
                .floor("1")
                .unitType(UnitType.FLAT)
                .monthlyMaintenanceAmount(BigDecimal.ZERO)
                .occupancyStatus(OccupancyStatus.SELF_OCCUPIED)
                .status("ACTIVE")
                .build();
        return entityManager.persist(unit);
    }

    private VisitorRequest newRequest(Unit unit, VisitorStatus status, LocalDateTime expiresAt) {
        return VisitorRequest.builder()
                .unit(unit)
                .visitorName("Delivery Person")
                .company("Amazon")
                .status(status)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void savesAndReadsBackStatusEnum() {
        Unit unit = persistUnit("A-101");
        VisitorRequest saved = repository.save(
                newRequest(unit, VisitorStatus.PENDING, LocalDateTime.now().plusMinutes(10)));
        entityManager.flush();
        entityManager.clear();

        VisitorRequest reloaded = repository.findById(saved.getVisitorRequestId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(VisitorStatus.PENDING);
        assertThat(reloaded.getVisitorName()).isEqualTo("Delivery Person");
        assertThat(reloaded.getCompany()).isEqualTo("Amazon");
        assertThat(reloaded.getUnit().getUnitNumber()).isEqualTo("A-101");
    }

    @Test
    void findByStatusReturnsOnlyMatchingRows() {
        Unit unit = persistUnit("A-102");
        repository.save(newRequest(unit, VisitorStatus.PENDING, LocalDateTime.now().plusMinutes(10)));
        repository.save(newRequest(unit, VisitorStatus.APPROVED, LocalDateTime.now().plusMinutes(10)));
        repository.save(newRequest(unit, VisitorStatus.PENDING, LocalDateTime.now().plusMinutes(10)));
        entityManager.flush();

        List<VisitorRequest> pending = repository.findByStatusOrderByCreatedOnDesc(VisitorStatus.PENDING);
        assertThat(pending).hasSize(2)
                .allMatch(r -> r.getStatus() == VisitorStatus.PENDING);
        assertThat(repository.countByStatus(VisitorStatus.APPROVED)).isEqualTo(1);
    }

    @Test
    void findExpiredPendingSelectsOnlyPastPendingRows() {
        Unit unit = persistUnit("A-103");
        LocalDateTime now = LocalDateTime.now();

        VisitorRequest expiredPending =
                repository.save(newRequest(unit, VisitorStatus.PENDING, now.minusMinutes(1)));
        // future PENDING — not yet expired
        repository.save(newRequest(unit, VisitorStatus.PENDING, now.plusMinutes(30)));
        // already decided, past expiry — must NOT be selected
        repository.save(newRequest(unit, VisitorStatus.APPROVED, now.minusMinutes(5)));
        entityManager.flush();

        List<VisitorRequest> expired = repository.findExpiredPending(VisitorStatus.PENDING, now);

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getVisitorRequestId()).isEqualTo(expiredPending.getVisitorRequestId());
    }

    @Test
    void findByStatusAndUnitIdsScopesToGivenUnits() {
        Unit unitA = persistUnit("A-104");
        Unit unitB = persistUnit("B-204");
        repository.save(newRequest(unitA, VisitorStatus.PENDING, LocalDateTime.now().plusMinutes(10)));
        repository.save(newRequest(unitB, VisitorStatus.PENDING, LocalDateTime.now().plusMinutes(10)));
        entityManager.flush();

        List<VisitorRequest> forA = repository.findByStatusAndUnitIds(
                VisitorStatus.PENDING, List.of(unitA.getUnitId()));

        assertThat(forA).hasSize(1);
        assertThat(forA.get(0).getUnit().getUnitId()).isEqualTo(unitA.getUnitId());
    }
}
