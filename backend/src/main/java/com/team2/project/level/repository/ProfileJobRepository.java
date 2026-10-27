package com.team2.project.level.repository;

import com.team2.project.level.domain.ProfileJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileJobRepository extends JpaRepository<ProfileJob, UUID> {
}
