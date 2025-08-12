package com.featureflags.repository;

import com.featureflags.model.FeatureFlagJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeatureFlagJobRepository extends JpaRepository<FeatureFlagJob, Long> {

    List<FeatureFlagJob> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<FeatureFlagJob> findByStatusOrderByCreatedAtDesc(FeatureFlagJob.JobStatus status);
}
