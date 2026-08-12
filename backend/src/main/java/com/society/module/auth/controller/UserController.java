package com.society.module.auth.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.module.auth.dto.UserDTO;
import com.society.module.auth.dto.UserCreateRequest;
import com.society.module.auth.dto.UserUpdateRequest;
import com.society.module.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CHAIRMAN') or hasRole('SECRETARY')")
    public ResponseEntity<ApiResponse<PagedResponse<UserDTO>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive) {
        PagedResponse<UserDTO> users = userService.getAllUsers(page, size, search, isActive);
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CHAIRMAN') or hasRole('SECRETARY')")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable Long userId) {
        UserDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SECRETARY')")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserDTO user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", user));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SECRETARY')")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        UserDTO user = userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    @PatchMapping("/{userId}/toggle-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDTO>> toggleUserStatus(@PathVariable Long userId) {
        UserDTO user = userService.toggleUserStatus(userId);
        return ResponseEntity.ok(ApiResponse.success("User status updated", user));
    }

    @PatchMapping("/{userId}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDTO>> assignRoles(
            @PathVariable Long userId,
            @RequestBody Map<String, List<String>> body) {
        List<String> roleNames = body.get("roles");
        UserDTO user = userService.assignRoles(userId, roleNames);
        return ResponseEntity.ok(ApiResponse.success("Roles updated successfully", user));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        userService.resetPassword(userId, newPassword);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }

    // ===== ROLES & PERMISSIONS =====

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<com.society.module.auth.dto.RoleDTO>>> getAllRoles() {
        List<com.society.module.auth.dto.RoleDTO> roles = userService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<com.society.module.auth.dto.PermissionDTO>>> getAllPermissions() {
        List<com.society.module.auth.dto.PermissionDTO> permissions = userService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<com.society.module.auth.dto.RoleDTO>> updateRolePermissions(
            @PathVariable Long roleId,
            @RequestBody Map<String, List<Long>> body) {
        List<Long> permissionIds = body.get("permissionIds");
        com.society.module.auth.dto.RoleDTO role = userService.updateRolePermissions(roleId, permissionIds);
        return ResponseEntity.ok(ApiResponse.success("Permissions updated for role", role));
    }
}
