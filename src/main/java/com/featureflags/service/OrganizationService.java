package com.featureflags.service;

import com.featureflags.model.Organization;
import com.featureflags.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import com.featureflags.model.OrganizationBulkResult;
import com.featureflags.model.OrganizationBulkResult.OperationStatus;

@Service
public class OrganizationService {
    private static final String ERROR_MULTIPLE_PARENTS = "An organization can only have single parent!";
    private static final String ERROR_CYCLE_EXISTS = "Cycle Exists!";
    private static final String SUCCESS_MESSAGE = "Organizations processed successfully!";

    private final OrganizationRepository organizationRepository;

    @Autowired
    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    private static final String ERROR_INVALID_ORGS = "Organizations list contains invalid entries!";

    @Transactional
    public OrganizationBulkResult processOrganizations(List<Organization> organizations) {
        if (organizations == null || organizations.isEmpty()) {
            return new OrganizationBulkResult(OperationStatus.SUCCESS, SUCCESS_MESSAGE);
        }

        // Validate all organizations in the list
        try {
            organizations.forEach(this::validateOrganization);
        } catch (IllegalArgumentException e) {
            return new OrganizationBulkResult(OperationStatus.FAILED, ERROR_INVALID_ORGS);
        }

        // Fetch existing organizations
        Set<Long> orgIds = organizations.stream()
                .map(Organization::getId)
                .collect(Collectors.toSet());
        List<Organization> existingOrgs = this.organizationRepository.findAllById(orgIds);

        // Combine existing and new organizations for validation
        List<Organization> allOrganizations = new ArrayList<>();
        allOrganizations.addAll(existingOrgs);
        allOrganizations.addAll(organizations);

        // Validate parent-child relationships
        HashMap<Long, Long> childToParentMap = new HashMap<>();
        if (!this.tryBuildChildParentMap(allOrganizations, childToParentMap)) {
            return new OrganizationBulkResult(OperationStatus.FAILED, ERROR_MULTIPLE_PARENTS);
        }

        // Check for cycles in the hierarchy
        if (this.isCyclic(allOrganizations)) {
            return new OrganizationBulkResult(OperationStatus.FAILED, ERROR_CYCLE_EXISTS);
        }

        // Save all organizations
        try {
            List<Organization> savedOrgs = this.organizationRepository.saveAll(organizations);
            return new OrganizationBulkResult(OperationStatus.SUCCESS, SUCCESS_MESSAGE,
                    savedOrgs.stream().map(org -> org.getId()).collect(Collectors.toList()));
        } catch (Exception e) {
            return new OrganizationBulkResult(OperationStatus.FAILED,
                    "Failed to save organizations: " + e.getMessage());
        }
    }

    private boolean tryBuildChildParentMap(List<Organization> organizations, HashMap<Long, Long> childToParentMap) {
        for (Organization org : organizations) {
            if (childToParentMap.containsKey(org.getId()) && childToParentMap.get(org.getId()) != org.getParentId()) {
                return false;
            }
            childToParentMap.put(org.getId(), org.getParentId());
        }
        return true;
    }

    private boolean isCyclic(List<Organization> organizations) {
        Map<Long, Set<Long>> parentToChildrenMap = buildParentChildrenMap(organizations);
        return hasCycle(parentToChildrenMap, organizations);
    }

    private Map<Long, Set<Long>> buildParentChildrenMap(List<Organization> organizations) {
        Map<Long, Set<Long>> parentToChildrenMap = new HashMap<>();

        // Build the parent-child relationships in a single pass
        organizations.forEach(org -> {
            // Add empty set for current org if not present
            parentToChildrenMap.computeIfAbsent(org.getId(), k -> new HashSet<>());

            // If org has a parent, add the child to parent's set
            if (org.getParentId() != null) {
                parentToChildrenMap.computeIfAbsent(org.getParentId(), k -> new HashSet<>())
                        .add(org.getId());
            }
        });

        return parentToChildrenMap;
    }

    private boolean hasCycle(Map<Long, Set<Long>> parentToChildrenMap, List<Organization> organizations) {
        Set<Long> visited = new HashSet<>();
        Set<Long> currentPath = new HashSet<>();

        // Try to find a cycle starting from each unvisited node
        for (Long orgId : parentToChildrenMap.keySet()) {
            if (!visited.contains(orgId) && hasCycleUtil(orgId, parentToChildrenMap, visited, currentPath)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycleUtil(Long orgId, Map<Long, Set<Long>> parentToChildrenMap,
            Set<Long> visited, Set<Long> currentPath) {
        visited.add(orgId);
        currentPath.add(orgId);

        // Check all children for cycles
        for (Long childId : parentToChildrenMap.getOrDefault(orgId, Collections.emptySet())) {
            if (!visited.contains(childId)) {
                if (hasCycleUtil(childId, parentToChildrenMap, visited, currentPath)) {
                    return true;
                }
            } else if (currentPath.contains(childId)) {
                // If we find a node that's in our current path, we have a cycle
                return true;
            }
        }

        currentPath.remove(orgId);
        return false;
    }

    private static final String ERROR_ORG_EXISTS_AS_CHILD = "Organization already exists and is assigned as a child to another organization!";
    private static final String ERROR_INVALID_ORG = "Organization cannot be null and must have a valid ID!";
    private static final String ERROR_INVALID_ID = "Organization ID cannot be null!";

    @Transactional
    public void addOrganization(Organization org) {
        validateOrganization(org);

        Organization orgFromDb = this.organizationRepository.findById(org.getId()).orElse(null);
        if (orgFromDb != null && orgFromDb.getParentId() != null) {
            throw new UnsupportedOperationException(ERROR_ORG_EXISTS_AS_CHILD);
        }
        this.organizationRepository.save(org);
    }

    public Organization getOrganization(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(ERROR_INVALID_ID);
        }
        return this.organizationRepository.findById(id).orElse(null);
    }

    private void validateOrganization(Organization org) {
        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException(ERROR_INVALID_ORG);
        }
    }
}