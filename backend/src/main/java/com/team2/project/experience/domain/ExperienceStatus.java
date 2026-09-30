package com.team2.project.experience.domain;

/** 체험 진행 상태. 앞으로만 이동한다 (IA 9장). */
public enum ExperienceStatus {
	STARTED, PRE_JUDGED, REVIEWING, REVIEWED, VERDICT_CONFIRMED, AI_REVEALED, COMPLETED
}
