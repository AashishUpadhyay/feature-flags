package com.featureflags.controller;

import com.featureflags.model.Organization;
import com.featureflags.model.OrganizationBulkResult;
import com.featureflags.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private OrganizationController organizationController;

    private static final Long ORG_ID = 1L;
    private static final Long PARENT_ID = 2L;
    private static final String ORG_NAME = "Test Organization";

    @BeforeEach
    void setUp() {
        // Any common setup can go here
    }

    @Test
    void processOrganizations_Success_ReturnsBulkResult() {
        // Given
        List<Organization> organizations = Arrays.asList(
                new Organization(1L, "Org1", null),
                new Organization(2L, "Org2", 1L));
        OrganizationBulkResult expectedResult = new OrganizationBulkResult(
                OrganizationBulkResult.OperationStatus.SUCCESS,
                "Successfully processed organizations");
        when(organizationService.processOrganizations(organizations)).thenReturn(expectedResult);

        // When
        ResponseEntity<OrganizationBulkResult> response = organizationController.processOrganizations(organizations);

        // Then
        assertNotNull(response);
        assertEquals(expectedResult.getStatus(), response.getBody().getStatus());
        assertEquals(expectedResult.getMessage(), response.getBody().getMessage());
        verify(organizationService).processOrganizations(organizations);
    }

    @Test
    void processOrganizations_Failure_ReturnsBulkResult() {
        // Given
        List<Organization> organizations = Arrays.asList(
                new Organization(1L, "Org1", null),
                new Organization(2L, "Org2", 999L) // Invalid parent ID
        );
        OrganizationBulkResult expectedResult = new OrganizationBulkResult(
                OrganizationBulkResult.OperationStatus.FAILED,
                "Invalid parent organization");
        when(organizationService.processOrganizations(organizations)).thenReturn(expectedResult);

        // When
        ResponseEntity<OrganizationBulkResult> response = organizationController.processOrganizations(organizations);

        // Then
        assertNotNull(response);
        assertEquals(OrganizationBulkResult.OperationStatus.FAILED, response.getBody().getStatus());
        assertEquals(expectedResult.getMessage(), response.getBody().getMessage());
        verify(organizationService).processOrganizations(organizations);
    }

    @Test
    void addOrganizationToParent_Success_ReturnsOk() {
        // Given
        doNothing().when(organizationService).addOrganization(any(Organization.class));

        // When
        ResponseEntity<Void> response = organizationController.addOrganizationToParent(ORG_ID, PARENT_ID, ORG_NAME);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        verify(organizationService).addOrganization(argThat(org -> org.getId().equals(ORG_ID) &&
                org.getParentId().equals(PARENT_ID) &&
                org.getName().equals(ORG_NAME)));
    }

    @Test
    void addOrganizationToParent_VerifiesOrganizationStructure() {
        // Given
        doNothing().when(organizationService).addOrganization(any(Organization.class));

        // When
        organizationController.addOrganizationToParent(ORG_ID, PARENT_ID, ORG_NAME);

        // Then
        verify(organizationService).addOrganization(argThat(org -> {
            assertNotNull(org, "Organization should not be null");
            assertEquals(ORG_ID, org.getId(), "Organization ID should match");
            assertEquals(PARENT_ID, org.getParentId(), "Parent ID should match");
            assertEquals(ORG_NAME, org.getName(), "Organization name should match");
            return true;
        }));
    }

    @Test
    void getOrganization_ReturnsOrganizationWhenFound() {
        // Given
        Organization expectedOrg = new Organization(ORG_ID, ORG_NAME, PARENT_ID);
        when(organizationService.getOrganization(ORG_ID)).thenReturn(expectedOrg);

        // When
        ResponseEntity<Organization> response = organizationController.getOrganization(ORG_ID);

        // Then
        verify(organizationService).getOrganization(ORG_ID);
        assertEquals(expectedOrg, response.getBody());
    }

    @Test
    void getOrganization_ReturnsNullWhenNotFound() {
        // Given
        when(organizationService.getOrganization(ORG_ID)).thenReturn(null);

        // When
        ResponseEntity<Organization> response = organizationController.getOrganization(ORG_ID);

        // Then
        verify(organizationService).getOrganization(ORG_ID);
        assertNull(response.getBody());
    }
}