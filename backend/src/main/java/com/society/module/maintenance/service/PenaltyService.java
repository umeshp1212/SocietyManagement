package com.society.module.maintenance.service;

import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.maintenance.dto.PenaltyDTO;
import com.society.module.maintenance.entity.Penalty;
import com.society.module.maintenance.entity.Penalty.PenaltyCategory;
import com.society.module.maintenance.entity.Penalty.PenaltyStatus;
import com.society.module.maintenance.repository.PenaltyRepository;
import com.society.module.owner.entity.Unit;
import com.society.module.owner.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PenaltyService {

    private final PenaltyRepository penaltyRepository;
    private final UnitRepository unitRepository;

    @Transactional
    public PenaltyDTO addPenalty(PenaltyDTO request) {
        Unit unit = unitRepository.findById(request.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "unitId", request.getUnitId()));

        Penalty penalty = Penalty.builder()
                .unit(unit)
                .amount(request.getAmount())
                .reason(request.getReason())
                .category(PenaltyCategory.valueOf(request.getCategory()))
                .billMonth(request.getBillMonth())
                .billYear(request.getBillYear())
                .status(PenaltyStatus.PENDING)
                .imposedBy(request.getImposedBy() != null ? request.getImposedBy() : "SYSTEM")
                .build();

        penalty = penaltyRepository.save(penalty);
        return mapToDTO(penalty);
    }

    public List<PenaltyDTO> getPenaltiesByUnit(Long unitId) {
        return penaltyRepository.findByUnit_UnitIdOrderByCreatedOnDesc(unitId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<PenaltyDTO> getPendingPenalties() {
        return penaltyRepository.findByStatusOrderByCreatedOnDesc(PenaltyStatus.PENDING)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<PenaltyDTO> getPenaltiesByMonthYear(int month, int year) {
        return penaltyRepository.findByBillMonthAndBillYearAndStatus(month, year, PenaltyStatus.PENDING)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public PenaltyDTO cancelPenalty(Long penaltyId) {
        Penalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new ResourceNotFoundException("Penalty", "penaltyId", penaltyId));

        if (penalty.getStatus() == PenaltyStatus.BILLED) {
            throw new BusinessException("Cannot cancel a penalty that is already billed");
        }

        penalty.setStatus(PenaltyStatus.CANCELLED);
        penalty = penaltyRepository.save(penalty);
        return mapToDTO(penalty);
    }

    private PenaltyDTO mapToDTO(Penalty penalty) {
        return PenaltyDTO.builder()
                .penaltyId(penalty.getPenaltyId())
                .unitId(penalty.getUnit().getUnitId())
                .unitNumber(penalty.getUnit().getUnitNumber())
                .amount(penalty.getAmount())
                .reason(penalty.getReason())
                .category(penalty.getCategory().name())
                .billMonth(penalty.getBillMonth())
                .billYear(penalty.getBillYear())
                .status(penalty.getStatus().name())
                .imposedBy(penalty.getImposedBy())
                .createdOn(penalty.getCreatedOn())
                .build();
    }
}
