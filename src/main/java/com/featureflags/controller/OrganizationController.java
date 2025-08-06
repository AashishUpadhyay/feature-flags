package com.featureflags.controller;

import com.featureflags.model.Organization;
import com.featureflags.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;

import com.featureflags.model.OrganizationBulkResult;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Autowired
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("/bulk")
    public ResponseEntity<OrganizationBulkResult> processOrganizations(
            @RequestBody List<Organization> organizations) {
        // Let the service handle all validations after sorting
        OrganizationBulkResult result = organizationService.processOrganizations(organizations);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orgId}/parent/{parentId}")
    public ResponseEntity<Void> addOrganizationToParent(
            @PathVariable Long orgId,
            @PathVariable Long parentId,
            @RequestParam String name) {
        Organization org = new Organization(name, parentId);
        org.setId(orgId);
        organizationService.addOrganization(org);
        return ResponseEntity.ok().build();
    }
}