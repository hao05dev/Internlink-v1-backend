package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.system.OnboardingProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaOnboardingProgressRepository extends JpaRepository<OnboardingProgress, OnboardingProgress.Key> {
    List<OnboardingProgress> findByUserIdOrderByTourKey(UUID userId);
}
