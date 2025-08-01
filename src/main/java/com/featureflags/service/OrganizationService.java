package com.featureflags.service;

import com.featureflags.model.Organization;
import com.featureflags.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;

    @Autowired
    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public void addOrganizations(List<Organization> orgs) {
        organizationRepository.saveAll(orgs);
    }

    @Transactional
    public void addOrganization(Organization org) {
        organizationRepository.save(org);
    }

    public Organization getOrganization(Long id) {
        return organizationRepository.findById(id).orElse(null);
    }

    public boolean isValidParentChild(Long parentId, Long childId) {
        if (parentId == null)
            return true;

        Optional<Organization> parentOrg = organizationRepository.findById(parentId);
        if (parentOrg.isEmpty())
            return false;

        Long currentId = parentId;
        Set<Long> visited = new HashSet<>();

        while (currentId != null) {
            if (currentId.equals(childId))
                return false; // Cyclic dependency
            if (!visited.add(currentId))
                return false; // Loop detected

            Organization org = organizationRepository.findById(currentId).orElse(null);
            currentId = org != null ? org.getParentId() : null;
        }

        return true;
    }
}