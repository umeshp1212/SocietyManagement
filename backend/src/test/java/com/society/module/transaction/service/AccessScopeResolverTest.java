package com.society.module.transaction.service;

import com.society.module.auth.entity.User;
import com.society.module.auth.repository.UserRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.tenant.entity.Tenant;
import com.society.module.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Example/unit tests for {@link AccessScopeResolver}.
 *
 * <p>Covers the five role/link mappings called out by the design: owner-only,
 * tenant-only, owner+tenant (union), no-link (empty member scope), and
 * society-wide role mappings. Also verifies the authorization failure when an
 * authenticated principal maps to no {@link User}.
 *
 * <p>Validates: Requirements 2.1, 2.5, 10.2, 10.4
 */
@ExtendWith(MockitoExtension.class)
class AccessScopeResolverTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private AccessScopeResolver resolver;

    private static final String USERNAME = "resident1";

    // ---- helpers -----------------------------------------------------------

    private Authentication authWithAuthorities(String... authorities) {
        var granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(USERNAME, "n/a", granted);
    }

    private Unit unit(Long unitId) {
        Unit u = new Unit();
        u.setUnitId(unitId);
        return u;
    }

    private Tenant tenantWithUnit(Long unitId) {
        Tenant t = new Tenant();
        t.setUnit(unit(unitId));
        return t;
    }

    private User member(Long ownerId, Long tenantId) {
        return User.builder()
                .userId(1L)
                .username(USERNAME)
                .password("x")
                .fullName("Resident One")
                .ownerId(ownerId)
                .tenantId(tenantId)
                .build();
    }

    @BeforeEach
    void memberIsResolvable() {
        // Default stub used by member scenarios; lenient so society-wide tests
        // (which never look up the user) don't trip the strict-stubbing check.
        lenient().when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(member(null, null)));
    }

    // ---- 1. owner-only -----------------------------------------------------

    @Test
    void ownerOnly_resolvesToMemberScopeWithOwnerUnitIds() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(member(10L, null)));
        when(unitRepository.findByOwnerId(10L))
                .thenReturn(List.of(unit(100L), unit(101L)));

        AccessScope scope = resolver.resolve(authWithAuthorities("ROLE_MEMBER"));

        assertThat(scope.societyWide()).isFalse();
        assertThat(scope.unitIds()).containsExactlyInAnyOrder(100L, 101L);
    }

    // ---- 2. tenant-only ----------------------------------------------------

    @Test
    void tenantOnly_resolvesToMemberScopeWithTenantUnitId() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(member(null, 20L)));
        when(tenantRepository.findById(20L))
                .thenReturn(Optional.of(tenantWithUnit(200L)));

        AccessScope scope = resolver.resolve(authWithAuthorities("ROLE_MEMBER"));

        assertThat(scope.societyWide()).isFalse();
        assertThat(scope.unitIds()).containsExactly(200L);
    }

    // ---- 3. owner + tenant -------------------------------------------------

    @Test
    void ownerAndTenant_resolvesToUnionOfUnitIds() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(member(10L, 20L)));
        when(unitRepository.findByOwnerId(10L))
                .thenReturn(List.of(unit(100L), unit(101L)));
        when(tenantRepository.findById(20L))
                .thenReturn(Optional.of(tenantWithUnit(200L)));

        AccessScope scope = resolver.resolve(authWithAuthorities("ROLE_MEMBER"));

        assertThat(scope.societyWide()).isFalse();
        assertThat(scope.unitIds()).containsExactlyInAnyOrder(100L, 101L, 200L);
    }

    @Test
    void ownerAndTenant_deduplicatesOverlappingUnitId() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(member(10L, 20L)));
        when(unitRepository.findByOwnerId(10L))
                .thenReturn(List.of(unit(100L)));
        when(tenantRepository.findById(20L))
                .thenReturn(Optional.of(tenantWithUnit(100L)));

        AccessScope scope = resolver.resolve(authWithAuthorities("ROLE_MEMBER"));

        assertThat(scope.unitIds()).containsExactly(100L);
    }

    // ---- 4. no-link (empty member scope) -----------------------------------

    @Test
    void noLink_resolvesToEmptyMemberScopeWithoutError() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(member(null, null)));

        AccessScope scope = resolver.resolve(authWithAuthorities("ROLE_MEMBER"));

        assertThat(scope.societyWide()).isFalse();
        assertThat(scope.unitIds()).isEmpty();
    }

    @Test
    void tenantLinkWithoutUnit_contributesNoUnitId() {
        Tenant tenantNoUnit = new Tenant();
        tenantNoUnit.setUnit(null);
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(member(null, 20L)));
        when(tenantRepository.findById(20L))
                .thenReturn(Optional.of(tenantNoUnit));

        AccessScope scope = resolver.resolve(authWithAuthorities("ROLE_MEMBER"));

        assertThat(scope.societyWide()).isFalse();
        assertThat(scope.unitIds()).isEmpty();
    }

    // ---- 5. society-wide role mappings -------------------------------------

    @Test
    void superAdmin_resolvesToSocietyWideScope() {
        AccessScope scope = resolver.resolve(authWithAuthorities("ROLE_SUPER_ADMIN"));

        assertThat(scope.societyWide()).isTrue();
        assertThat(scope.unitIds()).isEmpty();
    }

    @Test
    void committeeRoles_resolveToSocietyWideScope() {
        for (String role : Set.of("ROLE_CHAIRMAN", "ROLE_SECRETARY", "ROLE_TREASURER")) {
            AccessScope scope = resolver.resolve(authWithAuthorities(role));
            assertThat(scope.societyWide())
                    .as("role %s should be society-wide", role)
                    .isTrue();
        }
    }

    @Test
    void societyWideRoleTakesPrecedenceOverMemberLinks() {
        // A caller holding both a member role and a society-wide role is
        // classified society-wide and never triggers a user/unit lookup.
        AccessScope scope = resolver.resolve(
                authWithAuthorities("ROLE_MEMBER", "ROLE_TREASURER"));

        assertThat(scope.societyWide()).isTrue();
        assertThat(scope.unitIds()).isEmpty();
    }

    // ---- authorization failure ---------------------------------------------

    @Test
    void authenticatedPrincipalWithNoUser_throwsAccessDenied() {
        when(userRepository.findByUsername(USERNAME))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(authWithAuthorities("ROLE_MEMBER")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
