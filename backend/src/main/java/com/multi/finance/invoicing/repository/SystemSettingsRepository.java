package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
    Optional<SystemSettings> findByKey(String key);
}
