package com.featureflags.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFlagValidatorTest {

    private FeatureFlagValidator featureFlagValidator;

    @BeforeEach
    void setUp() throws IOException {
        featureFlagValidator = new FeatureFlagValidator();
        featureFlagValidator.init();
    }

    @Test
    void isFeatureFlagRegistered_WhenFlagExists_ReturnsTrue() {
        // Act & Assert - test each flag from feature-flags.json
        assertTrue(featureFlagValidator.isFeatureFlagRegistered("FeatureFlag1"));
        assertTrue(featureFlagValidator.isFeatureFlagRegistered("FeatureFlag2"));
        assertTrue(featureFlagValidator.isFeatureFlagRegistered("FeatureFlag3"));
        assertTrue(featureFlagValidator.isFeatureFlagRegistered("FeatureFlag4"));
        assertTrue(featureFlagValidator.isFeatureFlagRegistered("FeatureFlag5"));
    }

    @Test
    void isFeatureFlagRegistered_WhenFlagDoesNotExist_ReturnsFalse() {
        // Act & Assert
        assertFalse(featureFlagValidator.isFeatureFlagRegistered("NonExistentFlag"));
        assertFalse(featureFlagValidator.isFeatureFlagRegistered("UnknownFeature"));
        assertFalse(featureFlagValidator.isFeatureFlagRegistered(""));
    }

    @Test
    void isFeatureFlagRegistered_WhenFlagNameIsNull_ReturnsFalse() {
        // Act & Assert
        assertFalse(featureFlagValidator.isFeatureFlagRegistered(null));
    }

    @Test
    void isFeatureFlagRegistered_CaseSensitive_ReturnsFalse() {
        // Act & Assert - verify case sensitivity
        assertFalse(featureFlagValidator.isFeatureFlagRegistered("featureflag1"));
        assertFalse(featureFlagValidator.isFeatureFlagRegistered("FEATUREFLAG1"));
        assertFalse(featureFlagValidator.isFeatureFlagRegistered("featureFlag1"));
    }

    @Test
    void isFeatureFlagRegistered_WithWhitespace_ReturnsFalse() {
        // Act & Assert - verify whitespace handling
        assertFalse(featureFlagValidator.isFeatureFlagRegistered(" FeatureFlag1"));
        assertFalse(featureFlagValidator.isFeatureFlagRegistered("FeatureFlag1 "));
        assertFalse(featureFlagValidator.isFeatureFlagRegistered(" FeatureFlag1 "));
    }
}
