package com.society.module.transaction.service;

import com.society.module.auth.entity.User;
import com.society.module.auth.repository.UserRepository;
import com.society.module.owner.repository.UnitRepository;
import com.society.module.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Resolves the data {@link AccessScope} for an authenticated caller.
 *
 * <p>Society-wide access is PERMISSION driven: super admin, or any role that has been assigned
 * the {@code TRANSACTION_VIEW_ALL} permission (via the role-permission screen), may access all
 * transactions. Every other authenticated principal is mapped to the set of unit IDs they are
 * linked to as an owner and/or tenant; a member with no linked units resolves to an empty member
 * scope (which yields an empty result set downstream, not an error).
 *
 * <p>The society-wide predicate is centralized in {@link #isSocietyWide(Authentication)}
 * so list and detail flows classify callers identically.
 */
@Component
@RequiredArgsConstructor
public class AccessScopeResolver {

    /** Permission that grants society-wide (all units) transaction access. */
    public static final String TRANSACTION_VIEW_ALL = "TRANSACTION_VIEW_ALL";

    /** Authorities that grant society-wide access: super admin role, or the permission. */
    private static final Set<String> SOCIETY_WIDE_AUTHORITIES = Set.of(
            "ROLE_SUPER_ADMIN",
            TRANSACTION_VIEW_ALL
    );

    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;

    /**
     * Resolves the access scope for the given authenticated caller.
     *
     * @param auth the authenticated principal
     * @return a society-wide scope for super admin / holders of TRANSACTION_VIEW_ALL, otherwise a
     *         member scope limited to the caller's linked unit IDs
     * @throws AccessDeniedException when an authenticated principal maps to no
     *                               {@link User} record
     */
    public AccessScope resolve(Authentication auth) {
        if (isSocietyWide(auth)) {
            return AccessScope.societyWideScope();
        }

        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("No transaction access"));

        Set<Long> unitIds = new HashSet<>();

        if (user.getOwnerId() != null) {
            unitRepository.findByOwnerId(user.getOwnerId())
                    .forEach(unit -> unitIds.add(unit.getUnitId()));
        }

        if (user.getTenantId() != null) {
            tenantRepository.findById(user.getTenantId())
                    .filter(tenant -> tenant.getUnit() != null)
                    .ifPresent(tenant -> unitIds.add(tenant.getUnit().getUnitId()));
        }

        return AccessScope.memberScoped(unitIds);
    }

    /**
     * Centralized society-wide predicate: {@code true} when the caller is super admin or holds
     * the {@code TRANSACTION_VIEW_ALL} permission.
     *
     * @param auth the authenticated principal (may be {@code null})
     * @return whether the caller has unrestricted, society-wide access
     */
    private boolean isSocietyWide(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SOCIETY_WIDE_AUTHORITIES::contains);
    }
}
