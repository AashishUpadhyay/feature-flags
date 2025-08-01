package com.featureflags.controller;

import com.featureflags.model.FeatureFlag;
import com.featureflags.service.FeatureFlagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @Autowired
    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping("/{orgId}/{featureFlagName}")
    public ResponseEntity<FeatureFlag> getFeatureFlag(
            @PathVariable String orgId,
            @PathVariable String featureFlagName) {
        boolean value = featureFlagService.getFeatureFlag(orgId, featureFlagName);
        return ResponseEntity.ok(new FeatureFlag(orgId, featureFlagName, value));
    }

    @PostMapping("/{orgId}/{featureFlagName}/{value}")
    public ResponseEntity<FeatureFlag> setFeatureFlag(
            @PathVariable String orgId,
            @PathVariable String featureFlagName,
            @PathVariable boolean value) {
        featureFlagService.setFeatureFlag(orgId, featureFlagName, value);
        return ResponseEntity.ok(new FeatureFlag(orgId, featureFlagName, value));
    }
}