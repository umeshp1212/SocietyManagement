package com.society.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Authentication entry point invoked when an unauthenticated request reaches a
 * protected endpoint.
 *
 * <p>It records the rejected request in the application audit log (the dedicated
 * {@code SECURITY_AUDIT} logger) and then returns {@code 401 Unauthorized} so that
 * missing credentials remain distinguishable from an authenticated-but-forbidden
 * caller.</p>
 *
 * <p>Recording the rejection satisfies the audit requirement that authentication
 * failures be traceable (Req 6.4).</p>
 */
@Component
public class SecurityAuditEntryPoint implements AuthenticationEntryPoint {

    /** Dedicated audit logger so security rejections can be routed/retained independently. */
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {
        AUDIT_LOG.warn("Authentication rejected: unauthenticated request to {} {} - {}",
                request.getMethod(),
                request.getRequestURI(),
                authException != null ? authException.getMessage() : "no credentials");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}
