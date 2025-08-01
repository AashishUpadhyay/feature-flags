package com.featureflags.service;

import com.featureflags.model.FeatureFlag;
import com.featureflags.repository.FeatureFlagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
    private final FeatureFlagRepository featureFlagRepository;

    @Autowired
    public FeatureFlagService(FeatureFlagRepository featureFlagRepository) {
        this.featureFlagRepository = featureFlagRepository;
    }

    public boolean getFeatureFlag(Long organizationId, String featureFlagName) {
        return featureFlagRepository
                .findByOrganizationIdAndName(organizationId, featureFlagName)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }

    @Transactional
    public void setFeatureFlag(Long organizationId, String featureFlagName, boolean enabled) {
        FeatureFlag featureFlag = featureFlagRepository
                .findByOrganizationIdAndName(organizationId, featureFlagName)
                .orElseGet(() -> new FeatureFlag(featureFlagName, null, enabled, organizationId));

        featureFlag.setEnabled(enabled);
        featureFlagRepository.save(featureFlag);
    }
}