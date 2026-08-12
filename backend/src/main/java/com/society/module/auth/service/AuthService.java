package com.society.module.auth.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.auth.dto.*;
import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.RoleRepository;
import com.society.module.auth.repository.UserRepository;
import com.society.module.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BusinessException("Invalid username or password");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!user.getIsActive()) {
            throw new BusinessException("Account is deactivated. Contact administrator.");
        }

        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        return buildLoginResponse(user);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username '" + request.getUsername() + "' is already taken");
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email '" + request.getEmail() + "' is already registered");
        }

        // Resolve roles
        Set<Role> roles = new HashSet<>();
        if (request.getRoleNames() != null && !request.getRoleNames().isEmpty()) {
            for (String roleName : request.getRoleNames()) {
                Role role = roleRepository.findByRoleName(roleName)
                        .orElseThrow(() -> new BusinessException("Role '" + roleName + "' not found"));
                roles.add(role);
            }
        } else {
            // Default role: OWNER
            roleRepository.findByRoleName("OWNER").ifPresent(roles::add);
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .isActive(true)
                .roles(roles)
                .ownerId(request.getOwnerId())
                .tenantId(request.getTenantId())
                .build();

        user = userRepository.save(user);
        return buildLoginResponse(user);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedOn(LocalDateTime.now());
        userRepository.save(user);
    }

    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException("Invalid or expired refresh token");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!user.getIsActive()) {
            throw new BusinessException("Account is deactivated");
        }

        return buildLoginResponse(user);
    }

    public LoginResponse getCurrentUserInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());

        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getPermissionName)
                .distinct()
                .collect(Collectors.toList());

        String accessToken = jwtUtil.generateToken(user.getUsername(), user.getUserId(), roles, permissions);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
