package com.featureflags.repository;

import com.featureflags.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    List<Organization> findByParentId(Long parentId);

    @Query("SELECT o FROM Organization o WHERE o.parentId = :parentId")
    List<Organization> findDirectChildren(@Param("parentId") Long parentId);
}