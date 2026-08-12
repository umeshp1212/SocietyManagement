package com.society.module.settings.service;

import com.society.module.settings.entity.SocietySettings;
import com.society.module.settings.repository.SocietySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocietySettingsService {

    private final SocietySettingsRepository repository;

    public SocietySettings getSettings() {
        return repository.findAll().stream().findFirst()
                .orElse(getDefaultSettings());
    }

    @Transactional
    public SocietySettings saveSettings(SocietySettings settings) {
        SocietySettings existing = repository.findAll().stream().findFirst().orElse(null);
        if (existing != null) {
            settings.setId(existing.getId());
        }
        return repository.save(settings);
    }

    private SocietySettings getDefaultSettings() {
        return SocietySettings.builder()
                .societyName("ABC Cooperative Housing Society Ltd.")
                .addressLine1("Plot No. 123, Sector 5")
                .addressLine2("Near City Mall")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .registrationNumber("MH/HSG/12345/2010")
                .registrationDate("15-03-2010")
                .phone("020-12345678")
                .email("abc.society@email.com")
                .chairmanName("Mr. Chairman")
                .secretaryName("Mr. Secretary")
                .treasurerName("Mr. Treasurer")
                .build();
    }
}
