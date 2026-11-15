package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.UnitTagAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitTagAssignmentRepository extends JpaRepository<UnitTagAssignment, Long> {

    List<UnitTagAssignment> findByUnitTagId(Long unitTagId);

    List<UnitTagAssignment> findByUnitId(Long unitId);

    boolean existsByUnitIdAndUnitTagId(Long unitId, Long unitTagId);

    void deleteByUnitIdAndUnitTagId(Long unitId, Long unitTagId);

    void deleteByUnitTagId(Long unitTagId);

    long countByUnitTagId(Long unitTagId);
}
