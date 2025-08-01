package com.featureflags.health;

import com.featureflags.repository.FeatureFlagRepository;
import com.featureflags.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagHealthIndicator implements HealthIndicator {

    private final FeatureFlagRepository featureFlagRepository;
    private final OrganizationRepository organizationRepository;

    @Autowired
    public FeatureFlagHealthIndicator(FeatureFlagRepository featureFlagRepository,
            OrganizationRepository organizationRepository) {
        this.featureFlagRepository = featureFlagRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Health health() {
        try {
            // Try to access repositories to check database connectivity
            featureFlagRepository.count();
            organizationRepository.count();

            return Health.up()
                    .withDetail("featureFlags", "Database connection is healthy")
                    .withDetail("organizations", "Database connection is healthy")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}