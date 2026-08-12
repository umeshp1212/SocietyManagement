package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.Penalty;
import com.society.module.maintenance.entity.Penalty.PenaltyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, Long> {

    List<Penalty> findByUnit_UnitIdOrderByCreatedOnDesc(Long unitId);

    List<Penalty> findByBillMonthAndBillYearAndStatus(int month, int year, PenaltyStatus status);

    List<Penalty> findByUnit_UnitIdAndBillMonthAndBillYearAndStatus(
            Long unitId, int month, int year, PenaltyStatus status);

    List<Penalty> findByStatusOrderByCreatedOnDesc(PenaltyStatus status);
}
