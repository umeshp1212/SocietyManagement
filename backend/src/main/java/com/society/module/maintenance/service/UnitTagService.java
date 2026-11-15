package com.society.module.maintenance.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.UnitTagDTO;
import com.society.module.maintenance.entity.UnitTag;
import com.society.module.maintenance.entity.UnitTagAssignment;
import com.society.module.maintenance.repository.MaintenanceChargeConfigRepository;
import com.society.module.maintenance.repository.UnitTagAssignmentRepository;
import com.society.module.maintenance.repository.UnitTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnitTagService {

    private final UnitTagRepository tagRepository;
    private final UnitTagAssignmentRepository assignmentRepository;
    private final MaintenanceChargeConfigRepository chargeConfigRepository;

    // ---------------------------------------------------------------- Tag CRUD

    public List<UnitTagDTO> getActiveTags() {
        return tagRepository.findByIsActiveTrueOrderByTagNameAsc()
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<UnitTagDTO> getAllTags() {
        return tagRepository.findAll()
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public UnitTagDTO getTagById(Long id) {
        return mapToDTO(findTag(id));
    }

    @Transactional
    public UnitTagDTO createTag(UnitTagDTO dto) {
        String code = normalizeCode(dto.getTagCode());
        if (tagRepository.existsByTagCode(code)) {
            throw new BusinessException("Tag code already exists: " + code);
        }
        UnitTag tag = UnitTag.builder()
                .tagCode(code)
                .tagName(dto.getTagName())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        return mapToDTO(tagRepository.save(tag));
    }

    @Transactional
    public UnitTagDTO updateTag(Long id, UnitTagDTO dto) {
        UnitTag tag = findTag(id);
        // tagCode is the stable key charges reference; it is not editable here.
        tag.setTagName(dto.getTagName());
        tag.setDescription(dto.getDescription());
        tag.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return mapToDTO(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(Long id) {
        UnitTag tag = findTag(id);
        long chargesUsingTag = chargeConfigRepository.countByRequiredTagId(id);
        if (chargesUsingTag > 0) {
            throw new BusinessException("Cannot delete tag '" + tag.getTagName()
                    + "': it is still referenced by " + chargesUsingTag
                    + " charge configuration(s). Remove the tag from those charges first.");
        }
        // Clear any unit assignments, then remove the tag.
        assignmentRepository.deleteByUnitTagId(id);
        tagRepository.delete(tag);
    }

    // ------------------------------------------------------------- Assignments

    /**
     * Unit ids currently carrying the given tag.
     */
    public List<Long> getAssignedUnitIds(Long tagId) {
        findTag(tagId); // validate existence
        return assignmentRepository.findByUnitTagId(tagId)
                .stream().map(UnitTagAssignment::getUnitId).collect(Collectors.toList());
    }

    /**
     * Replace the full set of units assigned to a tag with {@code unitIds}.
     * Idempotent: existing assignments are kept, missing ones added, extras removed.
     */
    @Transactional
    public List<Long> setAssignedUnits(Long tagId, List<Long> unitIds) {
        findTag(tagId);
        Set<Long> desired = new LinkedHashSet<>(unitIds != null ? unitIds : List.of());

        List<UnitTagAssignment> existing = assignmentRepository.findByUnitTagId(tagId);
        Set<Long> current = existing.stream()
                .map(UnitTagAssignment::getUnitId).collect(Collectors.toSet());

        // Remove assignments no longer desired.
        List<UnitTagAssignment> toRemove = existing.stream()
                .filter(a -> !desired.contains(a.getUnitId()))
                .collect(Collectors.toList());
        if (!toRemove.isEmpty()) {
            assignmentRepository.deleteAll(toRemove);
        }

        // Add newly desired assignments.
        List<UnitTagAssignment> toAdd = new ArrayList<>();
        for (Long unitId : desired) {
            if (!current.contains(unitId)) {
                toAdd.add(UnitTagAssignment.builder()
                        .unitId(unitId)
                        .unitTagId(tagId)
                        .build());
            }
        }
        if (!toAdd.isEmpty()) {
            assignmentRepository.saveAll(toAdd);
        }

        return new ArrayList<>(desired);
    }

    // ------------------------------------------------------------------ helpers

    private UnitTag findTag(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit tag not found with id: " + id));
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase().replaceAll("\\s+", "_");
    }

    private UnitTagDTO mapToDTO(UnitTag tag) {
        return UnitTagDTO.builder()
                .unitTagId(tag.getUnitTagId())
                .tagCode(tag.getTagCode())
                .tagName(tag.getTagName())
                .description(tag.getDescription())
                .isActive(tag.getIsActive())
                .assignedUnitCount(assignmentRepository.countByUnitTagId(tag.getUnitTagId()))
                .build();
    }
}
