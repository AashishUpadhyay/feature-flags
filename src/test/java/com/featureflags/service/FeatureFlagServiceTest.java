package com.featureflags.service;

import com.featureflags.model.FeatureFlag;
import com.featureflags.repository.FeatureFlagRepository;
import com.featureflags.repository.FeatureFlagJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

        @Mock
        private FeatureFlagRepository featureFlagRepository;
        @Mock
        private FeatureFlagJobRepository jobRepository;
        @Mock
        private FeatureFlagBackgroundService backgroundService;
        @Mock
        private OrganizationService organizationService;

        private FeatureFlagService featureFlagService;

        @BeforeEach
        void setUp() {
                featureFlagService = new FeatureFlagService(featureFlagRepository, jobRepository, backgroundService,
                                organizationService);
        }

        @Test
        void getFeatureFlag_WhenFlagExists_ReturnsEnabledStatus() {
                // Arrange
                Long organizationId = 1L;
                String flagName = "test-flag";
                FeatureFlag featureFlag = new FeatureFlag(flagName, null, true, organizationId);
                when(featureFlagRepository.findByOrganizationIdAndName(organizationId, flagName))
                                .thenReturn(Optional.of(featureFlag));

                // Act
                boolean result = featureFlagService.getFeatureFlag(organizationId, flagName);

                // Assert
                assertTrue(result);
                verify(featureFlagRepository).findByOrganizationIdAndName(organizationId, flagName);
        }

        @Test
        void getFeatureFlag_WhenFlagDoesNotExist_ReturnsFalse() {
                // Arrange
                Long organizationId = 1L;
                String flagName = "non-existent-flag";
                when(featureFlagRepository.findByOrganizationIdAndName(organizationId, flagName))
                                .thenReturn(Optional.empty());

                // Act
                boolean result = featureFlagService.getFeatureFlag(organizationId, flagName);

                // Assert
                assertFalse(result);
                verify(featureFlagRepository).findByOrganizationIdAndName(organizationId, flagName);
        }

        @Test
        void setFeatureFlag_WhenFlagExists_UpdatesFlag() {
                // Arrange
                Long organizationId = 1L;
                String flagName = "test-flag";
                FeatureFlag existingFlag = new FeatureFlag(flagName, null, false, organizationId);
                when(featureFlagRepository.findByOrganizationIdAndName(organizationId, flagName))
                                .thenReturn(Optional.of(existingFlag));
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // Act
                featureFlagService.setFeatureFlag(organizationId, flagName, true);

                // Assert
                verify(featureFlagRepository).findByOrganizationIdAndName(organizationId, flagName);
                verify(featureFlagRepository).save(argThat(flag -> flag.getName().equals(flagName) &&
                                flag.getOrganizationId().equals(organizationId) &&
                                flag.isEnabled()));
        }

        @Test
        void setFeatureFlag_WhenFlagDoesNotExist_CreatesNewFlag() {
                // Arrange
                Long organizationId = 1L;
                String flagName = "new-flag";
                boolean enabled = true;
                when(featureFlagRepository.findByOrganizationIdAndName(organizationId, flagName))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // Act
                featureFlagService.setFeatureFlag(organizationId, flagName, enabled);

                // Assert
                verify(featureFlagRepository).findByOrganizationIdAndName(organizationId, flagName);
                verify(featureFlagRepository).save(argThat(flag -> flag.getName().equals(flagName) &&
                                flag.getOrganizationId().equals(organizationId) &&
                                flag.isEnabled() == enabled));
        }
}