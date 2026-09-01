package com.society.module.ownernoc.controller;

import com.society.common.ApiResponse;
import com.society.module.ownernoc.dto.NocTypeDTO;
import com.society.module.ownernoc.service.NocTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin CRUD for configurable NOC types. Viewing is available to any authenticated
 * user (needed by the member portal to populate the type dropdown); managing
 * requires NOC_TYPE_MANAGE or an admin role.
 */
@RestController
@RequestMapping("/noc-types")
@RequiredArgsConstructor
public class NocTypeController {

    private final NocTypeService nocTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NocTypeDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(nocTypeService.getAllTypes()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<NocTypeDTO>>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(nocTypeService.getActiveTypes()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY') or hasAuthority('NOC_TYPE_MANAGE')")
    public ResponseEntity<ApiResponse<NocTypeDTO>> create(@Valid @RequestBody NocTypeDTO dto) {
        NocTypeDTO created = nocTypeService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("NOC type created", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY') or hasAuthority('NOC_TYPE_MANAGE')")
    public ResponseEntity<ApiResponse<NocTypeDTO>> update(@PathVariable Long id, @RequestBody NocTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("NOC type updated", nocTypeService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','CHAIRMAN','SECRETARY') or hasAuthority('NOC_TYPE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        nocTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("NOC type deactivated", null));
    }
}
