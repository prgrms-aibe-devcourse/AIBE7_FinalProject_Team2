package com.team2.project.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 도전과제 "봤음" P-11 POST /achievements/seen (BE-83). id는 ProfileDecoCatalog.ACHIEVEMENTS의 id */
public record AchievementSeenRequest(@NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 40) String> achievementIds) {
}
