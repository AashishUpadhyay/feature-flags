package com.featureflags.controller;

import com.featureflags.model.FeatureFlag;
import com.featureflags.service.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagControllerTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private FeatureFlagController featureFlagController;

    private static final Long ORG_ID = 1L;
    private static final String FEATURE_FLAG_NAME = "test-feature";

    @BeforeEach
    void setUp() {
        // Any common setup can go here
    }

    @Test
    void getFeatureFlag_WhenEnabled_ReturnsEnabledFeatureFlag() {
        // Given
        when(featureFlagService.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME)).thenReturn(true);

        // When
        ResponseEntity<FeatureFlag> response = featureFlagController.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);

        // Then
        assertTrue(response.getBody().isEnabled());
        assertEquals(FEATURE_FLAG_NAME, response.getBody().getName());
        assertEquals(ORG_ID, response.getBody().getOrganizationId());
        verify(featureFlagService).getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);
    }

    @Test
    void getFeatureFlag_WhenDisabled_ReturnsDisabledFeatureFlag() {
        // Given
        when(featureFlagService.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME)).thenReturn(false);

        // When
        ResponseEntity<FeatureFlag> response = featureFlagController.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);

        // Then
        assertFalse(response.getBody().isEnabled());
        assertEquals(FEATURE_FLAG_NAME, response.getBody().getName());
        assertEquals(ORG_ID, response.getBody().getOrganizationId());
        verify(featureFlagService).getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);
    }

    @Test
    void setFeatureFlag_WithoutDescription_SetsFeatureFlagAndReturnsIt() {
        // Given
        boolean enabled = true;
        doNothing().when(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);

        // When
        ResponseEntity<FeatureFlag> response = featureFlagController.setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled,
                null);

        // Then
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEnabled());
        assertEquals(FEATURE_FLAG_NAME, response.getBody().getName());
        assertEquals(ORG_ID, response.getBody().getOrganizationId());
        assertNull(response.getBody().getDescription());
        verify(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);
    }

    @Test
    void setFeatureFlag_WithDescription_SetsFeatureFlagAndReturnsIt() {
        // Given
        boolean enabled = true;
        String description = "Test description";
        doNothing().when(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);

        // When
        ResponseEntity<FeatureFlag> response = featureFlagController.setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled,
                description);

        // Then
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEnabled());
        assertEquals(FEATURE_FLAG_NAME, response.getBody().getName());
        assertEquals(ORG_ID, response.getBody().getOrganizationId());
        assertEquals(description, response.getBody().getDescription());
        verify(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);
    }
}