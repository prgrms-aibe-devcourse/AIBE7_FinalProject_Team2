package com.team2.project.personality.dto;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.profile.domain.AchievementProgress;
import com.team2.project.profile.domain.ProfileDecoValues;
import com.team2.project.profile.domain.Rewards;
import com.team2.project.profile.service.ProfileDecoService;
import com.team2.project.profile.service.ProfileDecoService.ProfileDecoView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * P-5 마이페이지 (API 명세 9장). 기록이 없으면 null · 빈 배열인 빈 마이페이지
 */
public record MyPageResponse(
	PersonalityResultResponse personality,
	JudgmentPersonality judgmentPersonality,
	List<HistoryItem> history,
	List<ExperienceItem> experiences,
	Profile profile,
	ProfileDecoValues profileDeco,		// 꾸미기 저장값(없으면 기본값). 잠긴 값이어도 그대로 (BE-96)
	List<AchievementProgress> achievements,	// 도전과제 진행도 · 상태 · 보상 (서버 표가 원본, BE-96)
	Rewards unlocked) {				// 지금 열린 것: 처음부터 열린 것 + 달성한 도전과제의 보상 (BE-96)

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static MyPageResponse empty() {
		ProfileDecoView deco = ProfileDecoService.emptyView();
		return new MyPageResponse(null, null, List.of(), List.of(), Profile.EMPTY, deco.profileDeco(), deco.achievements(),
			deco.unlocked());
	}

	/** 프로필 수치. attendanceDays: 누적 출석 일수(BE-97, 기록이 없으면 0) */
	public record Profile(int attendanceDays) {

		static final Profile EMPTY = new Profile(0);
	}

	/** 종합 판결 성향. type은 네 축 모두 값이 있을 때만 있다 (성향 산식 4장) */
	public record JudgmentPersonality(Map<ValueAxis, Double> axisScores, PersonalityTypeResponse type,
		int experienceCount) {
	}

	/** 성향 변화 흐름의 한 점: 사건의 첫 회차 체험별 축 점수 (자료 없는 축은 null) */
	public record HistoryItem(Long caseId, String title, OffsetDateTime completedAt, Map<ValueAxis, Double> axisScores) {

		public static HistoryItem of(Experience experience, Instant fallbackCompletedAt, Map<ValueAxis, Double> axisScores) {
			Instant completedAt = experience.getCompletedAt() != null ? experience.getCompletedAt() : fallbackCompletedAt;
			return new HistoryItem(experience.getLegalCase().getId(), experience.getLegalCase().getTitle(),
				kst(completedAt), axisScores);
		}
	}

	/** 체험한 사건 (사건마다 최근 회차) */
	public record ExperienceItem(Long caseId, String title, CrimeType crimeType, ExperienceStatus status, int attemptNo,
		OffsetDateTime updatedAt) {

		public static ExperienceItem from(Experience experience) {
			return new ExperienceItem(experience.getLegalCase().getId(), experience.getLegalCase().getTitle(),
				experience.getLegalCase().getCrimeType(), experience.getStatus(), experience.getAttemptNo(),
				kst(experience.getUpdatedAt()));
		}
	}

	private static OffsetDateTime kst(Instant instant) {
		return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
	}
}
