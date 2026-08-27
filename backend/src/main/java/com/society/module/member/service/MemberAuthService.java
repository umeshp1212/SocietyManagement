package com.society.module.member.service;

import com.society.exception.BusinessException;
import com.society.module.auth.security.JwtUtil;
import com.society.module.member.dto.MemberLoginResponse;
import com.society.module.member.dto.MemberLoginResponse.MemberUnitInfo;
import com.society.module.owner.entity.Owner;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.entity.UnitOwner;
import com.society.module.owner.repository.OwnerRepository;
import com.society.module.owner.repository.UnitOwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberAuthService {

    private final OtpService otpService;
    private final OwnerRepository ownerRepository;
    private final UnitOwnerRepository unitOwnerRepository;
    private final JwtUtil jwtUtil;

    /**
     * Send OTP to the registered phone number.
     * Looks up the owner by phone to get their email for dual delivery.
     */
    /**
     * Send OTP to the registered phone number.
     * Returns masked email for UI display.
     */
    @Transactional
    public String sendOtp(String phone) {
        // Find owner by contact number to get email
        Owner owner = findOwnerByPhone(phone);

        // Generate and send OTP via email + console (SMS)
        otpService.generateAndSendOtp(phone, owner.getEmail());

        log.info("OTP sent for member phone: {}, owner: {}", phone, owner.getFullName());

        // Return masked email for display
        String email = owner.getEmail();
        if (email != null && !email.isBlank() && email.contains("@")) {
            String[] parts = email.split("@");
            String name = parts[0];
            String masked = name.substring(0, Math.min(2, name.length()))
                    + "****@" + parts[1];
            return masked;
        }
        return null;
    }

    /**
     * Verify OTP and generate JWT token for the member.
     * Returns member info with their units.
     */
    @Transactional
    public MemberLoginResponse verifyOtpAndLogin(String phone, String otp) {
        // Verify the OTP
        otpService.verifyOtp(phone, otp);

        // Find the owner
        Owner owner = findOwnerByPhone(phone);

        // Get all units owned by this owner
        List<UnitOwner> unitOwners = unitOwnerRepository.findByOwner_OwnerId(owner.getOwnerId());

        if (unitOwners.isEmpty()) {
            throw new BusinessException("No units found for this member. Contact society admin.");
        }

        List<MemberUnitInfo> units = unitOwners.stream()
                .map(uo -> {
                    Unit unit = uo.getUnit();
                    return MemberUnitInfo.builder()
                            .unitId(unit.getUnitId())
                            .unitNumber(unit.getUnitNumber())
                            .wing(unit.getWing())
                            .floor(unit.getFloor())
                            .build();
                })
                .collect(Collectors.toList());

        // Generate JWT with MEMBER role
        // Use phone as the "username" for member tokens
        String memberUsername = "member_" + phone;
        List<String> roles = List.of("MEMBER");
        List<String> permissions = List.of("MEMBER_DASHBOARD", "MEMBER_PAY");

        String accessToken = jwtUtil.generateToken(memberUsername, owner.getOwnerId(), roles, permissions);
        String refreshToken = jwtUtil.generateRefreshToken(memberUsername);

        log.info("Member login successful - phone: {}, owner: {}, units: {}",
                phone, owner.getFullName(), units.size());

        return MemberLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .ownerId(owner.getOwnerId())
                .ownerName(owner.getFullName())
                .phone(phone)
                .email(owner.getEmail())
                .units(units)
                .build();
    }

    private Owner findOwnerByPhone(String phone) {
        List<Owner> owners = ownerRepository.findByContactNumberOrAlternateNumber(phone);

        if (owners.isEmpty()) {
            throw new BusinessException("No member found with this phone number. Please contact society admin.");
        }

        // Return the first active owner found
        return owners.stream()
                .filter(o -> o.getStatus() == com.society.enums.OwnerStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Your account is inactive. Please contact society admin."));
    }
}
