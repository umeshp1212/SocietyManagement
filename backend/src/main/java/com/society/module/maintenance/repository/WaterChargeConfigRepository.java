package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.WaterChargeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WaterChargeConfigRepository extends JpaRepository<WaterChargeConfig, Long> {

    Optional<WaterChargeConfig> findByIsActiveTrue();
}
