package com.fieldops.analytics.infrastructure.persistence;

import com.fieldops.analytics.domain.model.ProjectionCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectionCheckpointRepository extends JpaRepository<ProjectionCheckpoint, String> {
}
