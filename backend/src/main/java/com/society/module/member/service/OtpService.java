package com.society.module.member.service;

import com.society.exception.BusinessException;
import com.society.module.member.entity.OtpToken;
import com.society.module.member.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final JavaMailSender mailSender;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.otp.rate-limit-per-hour:5}")
    private int rateLimitPerHour;

    @Value("${spring.mail.username:noreply@society.com}")
    private String fromEmail;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate a 6-digit OTP for the given phone, send via email and log to console for SMS.
     */
    @Transactional
    public void generateAndSendOtp(String phone, String email) {
        // Rate limiting - max N OTPs per phone per hour
        long recentCount = otpTokenRepository.countByPhoneAndCreatedAtAfter(
                phone, LocalDateTime.now().minusHours(1));
        if (recentCount >= rateLimitPerHour) {
            throw new BusinessException("Too many OTP requests. Please try again after some time.");
        }

        // Clear any previous unverified OTPs for this phone
        otpTokenRepository.deleteUnverifiedByPhone(phone);

        // Generate 6-digit OTP
        String otp = String.format("%06d", RANDOM.nextInt(1000000));

        // Save OTP token
        OtpToken otpToken = OtpToken.builder()
                .phone(phone)
                .otp(otp)
                .email(email)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .build();
        otpTokenRepository.save(otpToken);

        // Send OTP via email
        if (email != null && !email.isBlank()) {
            sendOtpEmail(email, otp);
        }

        // Log OTP for SMS integration (replace with actual SMS gateway later)
        log.info("===== OTP for phone {} : {} (expires in {} minutes) =====", phone, otp, otpExpiryMinutes);
    }

    /**
     * Verify the OTP for the given phone number.
     */
    @Transactional
    public boolean verifyOtp(String phone, String otp) {
        OtpToken otpToken = otpTokenRepository
                .findTopByPhoneAndVerifiedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new BusinessException("No OTP found for this phone number. Please request a new OTP."));

        // Check max attempts
        if (otpToken.getAttempts() >= maxAttempts) {
            otpTokenRepository.delete(otpToken);
            throw new BusinessException("Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        // Increment attempts
        otpToken.setAttempts(otpToken.getAttempts() + 1);
        otpTokenRepository.save(otpToken);

        // Check expiry
        if (otpToken.isExpired()) {
            otpTokenRepository.delete(otpToken);
            throw new BusinessException("OTP has expired. Please request a new OTP.");
        }

        // Verify OTP
        if (!otpToken.getOtp().equals(otp)) {
            int remaining = maxAttempts - otpToken.getAttempts();
            throw new BusinessException("Invalid OTP. " + remaining + " attempts remaining.");
        }

        // Mark as verified
        otpToken.setVerified(true);
        otpTokenRepository.save(otpToken);
        return true;
    }

    private void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Society Management - Your Login OTP");
            message.setText(
                    "Dear Member,\n\n" +
                    "Your OTP for Society Management Portal login is: " + otp + "\n\n" +
                    "This OTP is valid for " + otpExpiryMinutes + " minutes.\n" +
                    "Do not share this OTP with anyone.\n\n" +
                    "Regards,\nSociety Management"
            );
            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}. Error: {}", toEmail, e.getMessage());
            // Don't throw - OTP is still valid via SMS/console log
        }
    }
}
