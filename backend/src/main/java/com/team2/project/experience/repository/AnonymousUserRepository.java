package com.team2.project.experience.repository;

import com.team2.project.experience.domain.AnonymousUser;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, UUID> {
}
