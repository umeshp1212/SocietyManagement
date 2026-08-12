package com.society.module.auth.service;

import com.society.common.PagedResponse;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.auth.dto.*;
import com.society.module.auth.entity.Permission;
import com.society.module.auth.entity.Role;
import com.society.module.auth.entity.User;
import com.society.module.auth.repository.RoleRepository;
import com.society.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final com.society.module.auth.repository.PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    public PagedResponse<UserDTO> getAllUsers(int page, int size, String search, Boolean isActive) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        Page<User> userPage;

        if (search != null && !search.isBlank()) {
            userPage = userRepository.searchUsers(search, pageable);
        } else if (isActive != null) {
            userPage = userRepository.findByIsActive(isActive, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        List<UserDTO> content = userPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        return PagedResponse.<UserDTO>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    public UserDTO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        return mapToDTO(user);
    }

    @Transactional
    public UserDTO createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username '" + request.getUsername() + "' is already taken");
        }

        Set<Role> roles = resolveRoles(request.getRoles());

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
        return mapToDTO(user);
    }

    @Transactional
    public UserDTO updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setOwnerId(request.getOwnerId());
        user.setTenantId(request.getTenantId());

        if (request.getRoles() != null) {
            user.setRoles(resolveRoles(request.getRoles()));
        }

        user = userRepository.save(user);
        return mapToDTO(user);
    }

    @Transactional
    public UserDTO toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        user.setIsActive(!user.getIsActive());
        user = userRepository.save(user);
        return mapToDTO(user);
    }

    @Transactional
    public UserDTO assignRoles(Long userId, List<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        user.setRoles(resolveRoles(roleNames));
        user = userRepository.save(user);
        return mapToDTO(user);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("Password must be at least 6 characters");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToRoleDTO)
                .collect(Collectors.toList());
    }

    public List<PermissionDTO> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapToPermissionDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleDTO updateRolePermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "roleId", roleId));

        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        role.setPermissions(permissions);
        role = roleRepository.save(role);
        return mapToRoleDTO(role);
    }

    // ===== HELPERS =====

    private Set<Role> resolveRoles(List<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        if (roleNames != null) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByRoleName(roleName)
                        .orElseThrow(() -> new BusinessException("Role '" + roleName + "' not found"));
                roles.add(role);
            }
        }
        return roles;
    }

    private UserDTO mapToDTO(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());

        return UserDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .lastLogin(user.getLastLogin())
                .passwordChangedOn(user.getPasswordChangedOn())
                .roles(roles)
                .ownerId(user.getOwnerId())
                .tenantId(user.getTenantId())
                .createdOn(user.getCreatedOn())
                .build();
    }

    private RoleDTO mapToRoleDTO(Role role) {
        List<String> permissions = role.getPermissions().stream()
                .map(Permission::getPermissionName)
                .collect(Collectors.toList());

        return RoleDTO.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .displayName(role.getDisplayName())
                .description(role.getDescription())
                .permissions(permissions)
                .build();
    }

    private PermissionDTO mapToPermissionDTO(Permission permission) {
        return PermissionDTO.builder()
                .permissionId(permission.getPermissionId())
                .permissionName(permission.getPermissionName())
                .module(permission.getModule())
                .description(permission.getDescription())
                .build();
    }
}
