package com.team2.project.profile.repository;

import com.team2.project.profile.domain.ProfileDeco;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileDecoRepository extends JpaRepository<ProfileDeco, UUID> {
}
