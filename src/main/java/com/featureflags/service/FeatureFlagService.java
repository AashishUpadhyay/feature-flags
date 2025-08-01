package com.featureflags.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeatureFlagService {
    // Using ConcurrentHashMap for thread safety
    private final Map<String, Map<String, Boolean>> featureFlags = new ConcurrentHashMap<>();

    public boolean getFeatureFlag(String orgId, String featureFlagName) {
        return featureFlags
                .getOrDefault(orgId, new ConcurrentHashMap<>())
                .getOrDefault(featureFlagName, false);
    }

    public void setFeatureFlag(String orgId, String featureFlagName, boolean value) {
        featureFlags.computeIfAbsent(orgId, k -> new ConcurrentHashMap<>())
                .put(featureFlagName, value);
    }
}