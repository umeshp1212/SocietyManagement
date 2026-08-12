package com.society.module.owner.repository;

import com.society.module.owner.entity.OwnershipHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OwnershipHistoryRepository extends JpaRepository<OwnershipHistory, Long> {

    @Query("SELECT oh FROM OwnershipHistory oh JOIN FETCH oh.owner JOIN FETCH oh.unit " +
           "WHERE oh.unit.unitId = :unitId ORDER BY oh.ownershipStartDate DESC")
    List<OwnershipHistory> findByUnitIdOrderByStartDateDesc(@Param("unitId") Long unitId);

    @Query("SELECT oh FROM OwnershipHistory oh JOIN FETCH oh.owner JOIN FETCH oh.unit " +
           "WHERE oh.owner.ownerId = :ownerId ORDER BY oh.ownershipStartDate DESC")
    List<OwnershipHistory> findByOwnerIdOrderByStartDateDesc(@Param("ownerId") Long ownerId);

    @Query("SELECT oh FROM OwnershipHistory oh WHERE oh.unit.unitId = :unitId AND oh.ownershipEndDate IS NULL")
    Optional<OwnershipHistory> findCurrentOwnershipByUnitId(@Param("unitId") Long unitId);
}
