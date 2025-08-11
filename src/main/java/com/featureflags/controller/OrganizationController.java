package com.featureflags.controller;

import com.featureflags.model.Organization;
import com.featureflags.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        if (result.getMessage().contains("FAILED")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orgId}/parent/{parentId}")
    public ResponseEntity<Void> addOrganizationToParent(
            @PathVariable Long orgId,
            @PathVariable Long parentId,
            @RequestParam String name) {
        Organization org = new Organization(orgId, name, parentId);
        organizationService.addOrganization(org);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{orgId}")
    public ResponseEntity<Organization> getOrganization(
            @PathVariable Long orgId) {
        Organization org = organizationService.getOrganization(orgId);
        return ResponseEntity.ok(org);
    }
}