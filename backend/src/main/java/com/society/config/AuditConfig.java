package com.society.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditConfig {

    /** Fallback auditor when there is no authenticated principal (e.g. startup seeders, schedulers). */
    static final String SYSTEM_AUDITOR = "SYSTEM";

    /**
     * Resolves the current auditor (used for {@code @CreatedBy}/{@code @LastModifiedBy})
     * from the Spring Security context so audited entities record the acting user — for
     * example, the security guard who created a visitor/gate request. Falls back to
     * {@value #SYSTEM_AUDITOR} for unauthenticated contexts such as the startup data
     * seeder and scheduled jobs, preserving the previous behaviour there.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.of(SYSTEM_AUDITOR);
            }
            String name = authentication.getName();
            return Optional.of(name != null && !name.isBlank() ? name : SYSTEM_AUDITOR);
        };
    }
}
