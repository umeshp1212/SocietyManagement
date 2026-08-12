package com.society.module.owner.repository;

import com.society.module.owner.entity.UnitOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitOwnerRepository extends JpaRepository<UnitOwner, Long> {

    List<UnitOwner> findByUnit_UnitId(Long unitId);

    List<UnitOwner> findByOwner_OwnerId(Long ownerId);

    Optional<UnitOwner> findByUnit_UnitIdAndOwner_OwnerId(Long unitId, Long ownerId);

    @Query("SELECT uo FROM UnitOwner uo WHERE uo.unit.unitId = :unitId AND uo.isPrimary = true")
    Optional<UnitOwner> findPrimaryOwnerByUnitId(@Param("unitId") Long unitId);

    long countByUnit_UnitId(Long unitId);

    boolean existsByUnit_UnitIdAndOwner_OwnerId(Long unitId, Long ownerId);

    void deleteByUnit_UnitIdAndOwner_OwnerId(Long unitId, Long ownerId);
}
