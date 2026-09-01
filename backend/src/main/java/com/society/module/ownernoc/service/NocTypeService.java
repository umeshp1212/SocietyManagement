package com.society.module.ownernoc.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.ownernoc.dto.NocTypeDTO;
import com.society.module.ownernoc.entity.NocType;
import com.society.module.ownernoc.repository.NocTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NocTypeService {

    private final NocTypeRepository nocTypeRepository;

    public List<NocTypeDTO> getAllTypes() {
        return nocTypeRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<NocTypeDTO> getActiveTypes() {
        return nocTypeRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public NocTypeDTO create(NocTypeDTO dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new BusinessException("NOC type code is required");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("NOC type name is required");
        }
        NocType type = NocType.builder()
                .code(dto.getCode().trim().toUpperCase())
                .name(dto.getName())
                .description(dto.getDescription())
                .defaultTemplate(dto.getDefaultTemplate())
                .displayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
        return toDTO(nocTypeRepository.save(type));
    }

    @Transactional
    public NocTypeDTO update(Long id, NocTypeDTO dto) {
        NocType type = nocTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NOC Type", "nocTypeId", id));
        if (dto.getName() != null) type.setName(dto.getName());
        type.setDescription(dto.getDescription());
        type.setDefaultTemplate(dto.getDefaultTemplate());
        if (dto.getDisplayOrder() != null) type.setDisplayOrder(dto.getDisplayOrder());
        if (dto.getIsActive() != null) type.setIsActive(dto.getIsActive());
        return toDTO(nocTypeRepository.save(type));
    }

    @Transactional
    public void delete(Long id) {
        NocType type = nocTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NOC Type", "nocTypeId", id));
        // Soft-delete to preserve references from historical requests.
        type.setIsActive(false);
        nocTypeRepository.save(type);
    }

    private NocTypeDTO toDTO(NocType t) {
        return NocTypeDTO.builder()
                .nocTypeId(t.getNocTypeId())
                .code(t.getCode())
                .name(t.getName())
                .description(t.getDescription())
                .defaultTemplate(t.getDefaultTemplate())
                .displayOrder(t.getDisplayOrder())
                .isActive(t.getIsActive())
                .build();
    }
}
