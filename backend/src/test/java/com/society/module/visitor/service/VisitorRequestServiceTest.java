package com.society.module.visitor.service;

import com.society.common.FileUploadService;
import com.society.enums.TenantStatus;
import com.society.enums.VisitorStatus;
import com.society.exception.BusinessException;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.UnitOwnerRepository;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.service.SocietySettingsService;
import com.society.module.tenant.entity.Tenant;
import com.society.module.tenant.repository.TenantRepository;
import com.society.module.visitor.dto.VisitorRecipient;
import com.society.module.visitor.dto.VisitorRequestDTO;
import com.society.module.visitor.entity.VisitorRequest;
import com.society.module.visitor.repository.VisitorRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VisitorRequestService} (plan Task 3): recipient resolution
 * (multi-owner + active tenant, and the vacant/unassigned flat case) and request
 * creation (persist PENDING, expiry from the configurable timeout, guard notification),
 * plus the first-wins decision guards.
 */
class VisitorRequestServiceTest {

    private VisitorRequestRepository requestRepository;
    private UnitRepository unitRepository;
    private UnitOwnerRepository unitOwnerRepository;
    private TenantRepository tenantRepository;
    private SocietySettingsService settingsService;
    private FileUploadService fileUploadService;
    private VisitorNotifier notifier;
    private VisitorRequestService service;

    @BeforeEach
    void setUp() {
        requestRepository = mock(VisitorRequestRepository.class);
        unitRepository = mock(UnitRepository.class);
        unitOwnerRepository = mock(UnitOwnerRepository.class);
        tenantRepository = mock(TenantRepository.class);
        settingsService = mock(SocietySettingsService.class);
        fileUploadService = mock(FileUploadService.class);
        notifier = mock(VisitorNotifier.class);
        service = new VisitorRequestService(requestRepository, unitRepository, unitOwnerRepository,
                tenantRepository, settingsService, fileUploadService, notifier);

        // Default: save returns the entity it was given, with an id stamped.
        when(requestRepository.save(any(VisitorRequest.class))).thenAnswer(inv -> {
            VisitorRequest r = inv.getArgument(0);
            if (r.getVisitorRequestId() == null) {
                r.setVisitorRequestId(99L);
            }
            return r;
        });
        when(settingsService.getSettings()).thenReturn(
                SocietySettings.builder().societyName("Test").visitorApprovalTimeoutMinutes(15).build());
    }

    private Unit unit(Long id, String number) {
        return Unit.builder().unitId(id).unitNumber(number).wing("A").floor("1").build();
    }

    private UnitOwner ownerLink(Long ownerId, String name) {
        Owner owner = Owner.builder().ownerId(ownerId).fullName(name).contactNumber("90000000" + ownerId).build();
        return UnitOwner.builder().owner(owner).isPrimary(ownerId == 1L).build();
    }

    // ---------------- recipient resolution ----------------

    @Test
    void resolvesAllOwnersAndActiveTenant() {
        Unit unit = unit(5L, "A-101");
        when(unitOwnerRepository.findByUnit_UnitId(5L))
                .thenReturn(List.of(ownerLink(1L, "Alice"), ownerLink(2L, "Bob")));
        Tenant tenant = Tenant.builder().tenantId(7L).tenantName("Tina Tenant")
                .contactNumber("9111111111").build();
        when(tenantRepository.findActiveByUnitId(5L, TenantStatus.ACTIVE)).thenReturn(Optional.of(tenant));

        List<VisitorRecipient> recipients = service.resolveRecipients(unit);

        assertThat(recipients).hasSize(3);
        assertThat(recipients).filteredOn(r -> r.getType() == VisitorRecipient.Type.OWNER).hasSize(2);
        assertThat(recipients).anyMatch(r -> r.getType() == VisitorRecipient.Type.TENANT
                && r.getTenantId().equals(7L) && "Tina Tenant".equals(r.getName()));
    }

    @Test
    void resolvesOwnersOnlyWhenNoActiveTenant() {
        Unit unit = unit(6L, "A-102");
        when(unitOwnerRepository.findByUnit_UnitId(6L)).thenReturn(List.of(ownerLink(1L, "Alice")));
        when(tenantRepository.findActiveByUnitId(6L, TenantStatus.ACTIVE)).thenReturn(Optional.empty());

        List<VisitorRecipient> recipients = service.resolveRecipients(unit);

        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).getType()).isEqualTo(VisitorRecipient.Type.OWNER);
    }

    @Test
    void resolvesEmptyForVacantUnassignedFlat() {
        Unit unit = unit(8L, "A-999");
        when(unitOwnerRepository.findByUnit_UnitId(8L)).thenReturn(List.of());
        when(tenantRepository.findActiveByUnitId(8L, TenantStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.resolveRecipients(unit)).isEmpty();
    }

    // ---------------- createRequest ----------------

    @Test
    void createPersistsPendingWithExpiryAndNotifies() {
        Unit unit = unit(5L, "A-101");
        when(unitRepository.findById(5L)).thenReturn(Optional.of(unit));
        when(unitOwnerRepository.findByUnit_UnitId(5L)).thenReturn(List.of(ownerLink(1L, "Alice")));
        when(tenantRepository.findActiveByUnitId(5L, TenantStatus.ACTIVE)).thenReturn(Optional.empty());

        LocalDateTime before = LocalDateTime.now();
        VisitorRequestDTO dto = service.createRequest(5L, null, "Courier Guy", "Amazon", "parcel", null);

        assertThat(dto.getStatus()).isEqualTo("PENDING");
        assertThat(dto.getVisitorName()).isEqualTo("Courier Guy");
        assertThat(dto.getCompany()).isEqualTo("Amazon");
        assertThat(dto.getUnitNumber()).isEqualTo("A-101");
        assertThat(dto.getNotifiedRecipients()).containsExactly("Alice");
        // expiry ~15 minutes out (configurable timeout)
        assertThat(dto.getExpiresAt()).isAfter(before.plusMinutes(14));

        verify(notifier).notifyNewRequest(any(VisitorRequest.class), anyList());
        verify(fileUploadService, never()).uploadFile(any(), any());
    }

    @Test
    void createRejectsFlatWithNoRecipients() {
        Unit unit = unit(8L, "A-999");
        when(unitRepository.findById(8L)).thenReturn(Optional.of(unit));
        when(unitOwnerRepository.findByUnit_UnitId(8L)).thenReturn(List.of());
        when(tenantRepository.findActiveByUnitId(8L, TenantStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRequest(8L, null, "Courier", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No owner or active tenant");
        verify(requestRepository, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void createRequiresVisitorName() {
        assertThatThrownBy(() -> service.createRequest(5L, null, "  ", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Visitor name is required");
    }

    // ---------------- first-wins decision guards ----------------

    @Test
    void approveSetsApprovedForOwningCaller() {
        Unit unit = unit(5L, "A-101");
        VisitorRequest request = VisitorRequest.builder()
                .visitorRequestId(50L).unit(unit).visitorName("Courier")
                .status(VisitorStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));
        when(unitOwnerRepository.findByUnit_UnitId(5L)).thenReturn(List.of(ownerLink(1L, "Alice")));

        VisitorRequestDTO dto = service.approve(50L, 1L, "member_9000000011");

        assertThat(dto.getStatus()).isEqualTo("APPROVED");
        assertThat(dto.getDecidedBy()).isEqualTo("member_9000000011");
    }

    @Test
    void approveRejectedForNonOwningCaller() {
        Unit unit = unit(5L, "A-101");
        VisitorRequest request = VisitorRequest.builder()
                .visitorRequestId(51L).unit(unit).visitorName("Courier")
                .status(VisitorStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(requestRepository.findById(51L)).thenReturn(Optional.of(request));
        when(unitOwnerRepository.findByUnit_UnitId(5L)).thenReturn(List.of(ownerLink(1L, "Alice")));

        assertThatThrownBy(() -> service.approve(51L, 2L, "member_other"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("your own flat");
    }

    @Test
    void secondDecisionRejectedFirstWins() {
        Unit unit = unit(5L, "A-101");
        VisitorRequest request = VisitorRequest.builder()
                .visitorRequestId(52L).unit(unit).visitorName("Courier")
                .status(VisitorStatus.APPROVED).decidedBy("member_first")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        when(requestRepository.findById(52L)).thenReturn(Optional.of(request));
        when(unitOwnerRepository.findByUnit_UnitId(5L)).thenReturn(List.of(ownerLink(1L, "Alice")));

        assertThatThrownBy(() -> service.reject(52L, 1L, "member_second", "too late"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been approved");
    }

    @Test
    void timedOutCannotBeDecided() {
        Unit unit = unit(5L, "A-101");
        VisitorRequest request = VisitorRequest.builder()
                .visitorRequestId(53L).unit(unit).visitorName("Courier")
                .status(VisitorStatus.TIMED_OUT)
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(requestRepository.findById(53L)).thenReturn(Optional.of(request));
        when(unitOwnerRepository.findByUnit_UnitId(5L)).thenReturn(List.of(ownerLink(1L, "Alice")));

        assertThatThrownBy(() -> service.approve(53L, 1L, "member_late"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("timed_out");
    }

    // ---------------- expiry ----------------

    @Test
    void expireOverdueFlipsPendingToTimedOut() {
        VisitorRequest overdue = VisitorRequest.builder()
                .visitorRequestId(60L).unit(unit(5L, "A-101")).visitorName("Courier")
                .status(VisitorStatus.PENDING).expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(requestRepository.findExpiredPending(eq(VisitorStatus.PENDING), any()))
                .thenReturn(List.of(overdue));

        int expired = service.expireOverdueRequests();

        assertThat(expired).isEqualTo(1);
        assertThat(overdue.getStatus()).isEqualTo(VisitorStatus.TIMED_OUT);
        verify(requestRepository).saveAll(anyList());
    }

    // ---------------- re-push (nag until action) ----------------

    @Test
    void repushNotifiesEveryLivePendingRequest() {
        Unit unitA = unit(5L, "A-101");
        Unit unitB = unit(6L, "A-102");
        VisitorRequest r1 = VisitorRequest.builder()
                .visitorRequestId(70L).unit(unitA).visitorName("Courier")
                .status(VisitorStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        VisitorRequest r2 = VisitorRequest.builder()
                .visitorRequestId(71L).unit(unitB).visitorName("Food")
                .status(VisitorStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        when(requestRepository.findLivePending(eq(VisitorStatus.PENDING), any()))
                .thenReturn(List.of(r1, r2));
        when(unitOwnerRepository.findByUnit_UnitId(5L)).thenReturn(List.of(ownerLink(1L, "Alice")));
        when(unitOwnerRepository.findByUnit_UnitId(6L)).thenReturn(List.of(ownerLink(2L, "Bob")));

        int repushed = service.repushPendingRequests();

        assertThat(repushed).isEqualTo(2);
        verify(notifier, times(2)).notifyNewRequest(any(VisitorRequest.class), anyList());
    }

    @Test
    void repushSkipsRequestWithNoRecipientsAndNeverThrows() {
        Unit unit = unit(8L, "A-999");
        VisitorRequest r = VisitorRequest.builder()
                .visitorRequestId(72L).unit(unit).visitorName("Courier")
                .status(VisitorStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        when(requestRepository.findLivePending(eq(VisitorStatus.PENDING), any()))
                .thenReturn(List.of(r));
        when(unitOwnerRepository.findByUnit_UnitId(8L)).thenReturn(List.of());
        when(tenantRepository.findActiveByUnitId(8L, com.society.enums.TenantStatus.ACTIVE))
                .thenReturn(Optional.empty());

        int repushed = service.repushPendingRequests();

        assertThat(repushed).isEqualTo(0);
        verifyNoInteractions(notifier);
    }
}
