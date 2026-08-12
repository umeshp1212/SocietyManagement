package com.society.module.owner.repository;

import com.society.enums.OccupancyStatus;
import com.society.enums.UnitType;
import com.society.module.owner.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    Optional<Unit> findByUnitNumber(String unitNumber);

    boolean existsByUnitNumber(String unitNumber);

    Page<Unit> findByUnitType(UnitType unitType, Pageable pageable);

    Page<Unit> findByOccupancyStatus(OccupancyStatus occupancyStatus, Pageable pageable);

    Page<Unit> findByUnitTypeAndOccupancyStatus(UnitType unitType, OccupancyStatus occupancyStatus, Pageable pageable);

    Page<Unit> findByWing(String wing, Pageable pageable);

    @Query("SELECT DISTINCT u FROM Unit u JOIN u.unitOwners uo WHERE uo.owner.ownerId = :ownerId")
    List<Unit> findByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT u FROM Unit u WHERE " +
           "LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.wing) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Unit> searchUnits(@Param("search") String search);

    @Query("SELECT COUNT(u) FROM Unit u WHERE u.occupancyStatus = :status")
    long countByOccupancyStatus(@Param("status") OccupancyStatus status);

    @Query("SELECT COUNT(u) FROM Unit u WHERE u.unitType = :type")
    long countByUnitType(@Param("type") UnitType type);

    @Query("SELECT u FROM Unit u WHERE u.unitOwners IS EMPTY")
    List<Unit> findUnitsWithNoOwners();
}
