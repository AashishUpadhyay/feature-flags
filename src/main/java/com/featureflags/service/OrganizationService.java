package com.featureflags.service;

import com.featureflags.model.Organization;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrganizationService {
    private final Map<String, Organization> organizations = new ConcurrentHashMap<>();

    public void addOrganizations(List<Organization> orgs) {
        orgs.forEach(org -> organizations.put(org.getId(), org));
    }

    public void addOrganization(Organization org) {
        organizations.put(org.getId(), org);
    }

    public Organization getOrganization(String id) {
        return organizations.get(id);
    }

    public boolean isValidParentChild(String parentId, String childId) {
        if (parentId == null)
            return true;
        if (!organizations.containsKey(parentId))
            return false;

        String currentId = parentId;
        Set<String> visited = new HashSet<>();

        while (currentId != null) {
            if (currentId.equals(childId))
                return false; // Cyclic dependency
            if (!visited.add(currentId))
                return false; // Loop detected

            Organization org = organizations.get(currentId);
            currentId = org != null ? org.getParentId() : null;
        }

        return true;
    }
}