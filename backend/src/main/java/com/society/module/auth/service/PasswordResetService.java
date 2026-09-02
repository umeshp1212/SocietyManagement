package com.society.module.auth.service;

import com.society.exception.BusinessException;
import com.society.module.auth.entity.PasswordResetToken;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.PasswordResetTokenRepository;
import com.society.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    /**
     * Base path the Angular app is served under (its build baseHref), e.g. "/app" in
     * production. Kept SEPARATE from frontend-url because frontend-url is also used as the
     * CORS allowed origin, which must be scheme+host only (no path). Defaults to empty for
     * local dev where the app is served at the root.
     */
    @Value("${app.frontend-base-path:}")
    private String frontendBasePath;

    @Value("${app.password-reset.token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    @Value("${spring.mail.username:noreply@societymanagement.com}")
    private String fromEmail;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository tokenRepository,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Initiate password reset by sending an email with a reset link.
     * Always returns success to prevent email enumeration attacks.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !user.getIsActive()) {
            // Don't reveal whether the email exists - just log and return
            log.warn("Password reset requested for non-existent or inactive email: {}", email);
            return;
        }

        // Invalidate any existing tokens for this user
        tokenRepository.deleteByUser(user);

        // Generate a new token
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(tokenExpiryMinutes))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();

        tokenRepository.save(resetToken);

        // Send email
        sendResetEmail(user, token);
    }

    /**
     * Validate the reset token without consuming it.
     */
    public boolean validateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByTokenAndUsedFalse(token)
                .orElse(null);

        return resetToken != null && !resetToken.isExpired();
    }

    /**
     * Reset the password using the token.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new BusinessException("Invalid or expired reset token"));

        if (resetToken.isExpired()) {
            throw new BusinessException("Reset token has expired. Please request a new one.");
        }

        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("Password must be at least 6 characters");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedOn(LocalDateTime.now());
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Password reset successful for user: {}", user.getUsername());
    }

    /**
     * Build a user-facing frontend URL: frontend host + optional app base path + relative
     * path. Tolerates trailing/leading slashes and an empty base path.
     * e.g. host="https://ppvcd.in", basePath="/app", rel="/reset-password?token=x"
     *      -> "https://ppvcd.in/app/reset-password?token=x"
     */
    private String buildFrontendUrl(String relativePath) {
        String host = frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "";
        String base = frontendBasePath != null ? frontendBasePath.trim() : "";
        if (!base.isEmpty()) {
            if (!base.startsWith("/")) base = "/" + base;
            base = base.replaceAll("/+$", "");
        }
        String rel = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return host + base + rel;
    }

    private void sendResetEmail(User user, String token) {
        String resetLink = buildFrontendUrl("/reset-password?token=" + token);

        if (mailSender == null) {
            log.warn("Mail sender not configured. Reset link for user {}: {}", user.getUsername(), resetLink);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(user.getEmail());
            message.setSubject("Society Management - Password Reset Request");
            message.setText(
                    "Dear " + user.getFullName() + ",\n\n" +
                    "We received a request to reset your password for your Society Management account.\n\n" +
                    "Click the link below to reset your password:\n" +
                    resetLink + "\n\n" +
                    "This link will expire in " + tokenExpiryMinutes + " minutes.\n\n" +
                    "If you did not request this, please ignore this email. Your password will remain unchanged.\n\n" +
                    "Regards,\n" +
                    "Society Management Team"
            );

            mailSender.send(message);
            log.info("Password reset email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}. Error: {}", user.getEmail(), e.getMessage());
            log.info("Reset link for user {}: {}", user.getUsername(), resetLink);
            // Don't throw - the token is still valid, user can retry
        }
    }
}
