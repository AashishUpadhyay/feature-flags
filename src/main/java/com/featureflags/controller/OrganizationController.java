package com.featureflags.controller;

import com.featureflags.model.Organization;
import com.featureflags.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    @Autowired
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("/bulk")
    public ResponseEntity<Void> addOrganizations(@RequestBody List<Organization> organizations) {
        // Validate parent-child relationships
        for (Organization org : organizations) {
            if (!organizationService.isValidParentChild(org.getParentId(), org.getId())) {
                return ResponseEntity.badRequest().build();
            }
        }
        organizationService.addOrganizations(organizations);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orgId}/parent/{parentId}")
    public ResponseEntity<Void> addOrganizationToParent(
            @PathVariable String orgId,
            @PathVariable String parentId) {
        if (!organizationService.isValidParentChild(parentId, orgId)) {
            return ResponseEntity.badRequest().build();
        }
        organizationService.addOrganization(new Organization(orgId, parentId));
        return ResponseEntity.ok().build();
    }
}