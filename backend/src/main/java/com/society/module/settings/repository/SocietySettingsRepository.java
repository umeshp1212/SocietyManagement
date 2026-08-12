package com.society.module.settings.repository;

import com.society.module.settings.entity.SocietySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SocietySettingsRepository extends JpaRepository<SocietySettings, Long> {
}
