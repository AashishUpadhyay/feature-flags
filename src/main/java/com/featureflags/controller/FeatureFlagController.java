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
            @PathVariable Long orgId,
            @PathVariable String featureFlagName) {
        boolean enabled = featureFlagService.getFeatureFlag(orgId, featureFlagName);
        FeatureFlag featureFlag = new FeatureFlag(featureFlagName, null, enabled, orgId);
        return ResponseEntity.ok(featureFlag);
    }

    @PostMapping("/{orgId}/{featureFlagName}/{enabled}")
    public ResponseEntity<FeatureFlag> setFeatureFlag(
            @PathVariable Long orgId,
            @PathVariable String featureFlagName,
            @PathVariable boolean enabled,
            @RequestParam(required = false) String description) {
        featureFlagService.setFeatureFlag(orgId, featureFlagName, enabled);
        FeatureFlag featureFlag = new FeatureFlag(featureFlagName, description, enabled, orgId);
        return ResponseEntity.ok(featureFlag);
    }
}