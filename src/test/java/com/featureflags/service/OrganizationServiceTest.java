package com.featureflags.service;

import com.featureflags.model.Organization;
import com.featureflags.model.OrganizationBulkResult;
import com.featureflags.model.OrganizationBulkResult.OperationStatus;
import com.featureflags.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private OrganizationService organizationService;

    private Organization org1;
    private Organization org2;
    private Organization org3;

    @BeforeEach
    void setUp() {
        org1 = new Organization("Parent Org", null);
        org1.setId(1L);

        org2 = new Organization("Child Org", 1L);
        org2.setId(2L);

        org3 = new Organization("Grandchild Org", 2L);
        org3.setId(3L);
    }

    @Test
    void processOrganizations_Success() {
        // Arrange
        List<Organization> organizations = Arrays.asList(org1, org2);
        when(organizationRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(organizationRepository.saveAll(any())).thenReturn(organizations);

        // Act
        OrganizationBulkResult result = organizationService.processOrganizations(organizations);

        // Assert
        assertEquals(OperationStatus.SUCCESS, result.getStatus());
        assertEquals("Organizations processed successfully!", result.getMessage());
        verify(organizationRepository).saveAll(organizations);
    }

    @Test
    void processOrganizations_DuplicateParent() {
        // Arrange
        Organization duplicateOrg = new Organization("Duplicate Child", 1L);
        duplicateOrg.setId(2L);
        List<Organization> organizations = Arrays.asList(org2, duplicateOrg);

        // Act
        OrganizationBulkResult result = organizationService.processOrganizations(organizations);

        // Assert
        assertEquals(OperationStatus.SUCCESS, result.getStatus());
        verify(organizationRepository).saveAll(organizations);
    }

    @Test
    void processOrganizations_CyclicDependency() {
        // Arrange
        // Create a cycle: 1 -> 2 -> 3 -> 1
        org1.setParentId(3L);
        List<Organization> organizations = Arrays.asList(org1, org2, org3);

        // Act
        OrganizationBulkResult result = organizationService.processOrganizations(organizations);

        // Assert
        assertEquals(OperationStatus.FAILED, result.getStatus());
        assertEquals("Cycle Exists!", result.getMessage());
        verify(organizationRepository, never()).saveAll(any());
    }

    @Test
    void processOrganizations_ExistingOrganizationsWithCycle() {
        // Arrange
        List<Organization> organizations = Arrays.asList(org1, org2);
        when(organizationRepository.findAllById(any())).thenReturn(Arrays.asList(
                new Organization("Existing Org 1", 2L),
                new Organization("Existing Org 2", 1L)));

        // Act
        OrganizationBulkResult result = organizationService.processOrganizations(organizations);

        // Assert
        assertEquals(OperationStatus.FAILED, result.getStatus());
        assertEquals("An organization can only have single parent!", result.getMessage());
        verify(organizationRepository, never()).saveAll(any());
    }

    @Test
    void processOrganizations_EmptyList() {
        // Arrange
        List<Organization> organizations = Collections.emptyList();

        // Act
        OrganizationBulkResult result = organizationService.processOrganizations(organizations);

        // Assert
        assertEquals(OperationStatus.SUCCESS, result.getStatus());
        assertEquals("Organizations processed successfully!", result.getMessage());
        verify(organizationRepository, never()).saveAll(any());
    }

    @Test
    void processOrganizations_SingleOrganization() {
        // Arrange
        List<Organization> organizations = Collections.singletonList(org1);
        when(organizationRepository.findAllById(any())).thenReturn(Collections.emptyList());
        when(organizationRepository.saveAll(any())).thenReturn(organizations);

        // Act
        OrganizationBulkResult result = organizationService.processOrganizations(organizations);

        // Assert
        assertEquals(OperationStatus.SUCCESS, result.getStatus());
        assertEquals("Organizations processed successfully!", result.getMessage());
        verify(organizationRepository).saveAll(organizations);
    }

    @Test
    void processOrganizations_NullInput() {
        // Act
        OrganizationBulkResult result = organizationService.processOrganizations(null);

        // Assert
        assertEquals(OperationStatus.SUCCESS, result.getStatus());
        assertEquals("Organizations processed successfully!", result.getMessage());
        verify(organizationRepository, never()).saveAll(any());
    }

    @Test
    void addOrganization_Success() {
        // Arrange
        when(organizationRepository.findById(org1.getId())).thenReturn(Optional.empty());
        when(organizationRepository.save(org1)).thenReturn(org1);

        // Act
        organizationService.addOrganization(org1);

        // Assert
        verify(organizationRepository).save(org1);
    }

    @Test
    void addOrganization_ExistingChildOrganization() {
        // Arrange
        Organization existingOrg = new Organization("Existing Org", 3L);
        existingOrg.setId(1L);
        when(organizationRepository.findById(org1.getId())).thenReturn(Optional.of(existingOrg));

        // Act & Assert
        assertThrows(UnsupportedOperationException.class, () -> organizationService.addOrganization(org1));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void addOrganization_ExistingParentOrganization() {
        // Arrange
        Organization existingOrg = new Organization("Existing Org", null);
        existingOrg.setId(1L);
        when(organizationRepository.findById(org1.getId())).thenReturn(Optional.of(existingOrg));
        when(organizationRepository.save(org1)).thenReturn(org1);

        // Act
        organizationService.addOrganization(org1);

        // Assert
        verify(organizationRepository).save(org1);
    }

    @Test
    void getOrganization_ExistingOrganization() {
        // Arrange
        when(organizationRepository.findById(org1.getId())).thenReturn(Optional.of(org1));

        // Act
        Organization result = organizationService.getOrganization(org1.getId());

        // Assert
        assertNotNull(result);
        assertEquals(org1.getId(), result.getId());
    }

    @Test
    void getOrganization_NonExistentOrganization() {
        // Arrange
        when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Organization result = organizationService.getOrganization(999L);

        // Assert
        assertNull(result);
    }
}