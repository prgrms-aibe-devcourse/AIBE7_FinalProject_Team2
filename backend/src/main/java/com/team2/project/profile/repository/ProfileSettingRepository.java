package com.team2.project.profile.repository;

import com.team2.project.profile.domain.ProfileSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileSettingRepository extends JpaRepository<ProfileSetting, Long> {
}
