package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.MaintenanceChargeConfig;
import com.society.module.maintenance.entity.MaintenanceChargeConfig.ApplicableTo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceChargeConfigRepository extends JpaRepository<MaintenanceChargeConfig, Long> {

    List<MaintenanceChargeConfig> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<MaintenanceChargeConfig> findByApplicableToAndIsActiveTrueOrderByDisplayOrderAsc(ApplicableTo applicableTo);

    Optional<MaintenanceChargeConfig> findByChargeCode(String chargeCode);

    boolean existsByChargeCode(String chargeCode);
}
