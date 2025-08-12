package com.featureflags.controller;

import com.featureflags.model.FeatureFlag;
import com.featureflags.service.FeatureFlagService;
import com.featureflags.service.FeatureFlagValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagValidator featureFlagValidator;

    @Autowired
    public FeatureFlagController(FeatureFlagService featureFlagService, FeatureFlagValidator featureFlagValidator) {
        this.featureFlagService = featureFlagService;
        this.featureFlagValidator = featureFlagValidator;
    }

    @GetMapping("/{orgId}/{featureFlagName}")
    public ResponseEntity<FeatureFlag> getFeatureFlag(
            @PathVariable Long orgId,
            @PathVariable String featureFlagName) {
        if (!featureFlagValidator.isFeatureFlagRegistered(featureFlagName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Feature flag '" + featureFlagName + "' is not registered");
        }
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
        if (!featureFlagValidator.isFeatureFlagRegistered(featureFlagName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Feature flag '" + featureFlagName + "' is not registered");
        }
        featureFlagService.setFeatureFlag(orgId, featureFlagName, enabled);
        FeatureFlag featureFlag = new FeatureFlag(featureFlagName, description, enabled, orgId);
        return ResponseEntity.ok(featureFlag);
    }
}