package com.team2.project.personality.dto;

import com.team2.project.collection.dto.CollectionView;
import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.level.dto.LevelStatus;
import com.team2.project.profile.domain.AchievementProgress;
import com.team2.project.profile.domain.ProfileDecoValues;
import com.team2.project.profile.domain.ProfileSettingsValues;
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
	InitialPersonality initialPersonality,	// 처음 성향(초심, v0.50 BE-104). 없으면 null
	BaselineRetake baselineRetake,			// 성향카드 재심(v0.50 BE-104)
	JudgmentPersonality judgmentPersonality,
	List<HistoryItem> history,
	List<ExperienceItem> experiences,
	Profile profile,
	ProfileDecoValues profileDeco,		// 꾸미기 저장값(없으면 기본값). 잠긴 값이어도 그대로 (BE-96)
	List<AchievementProgress> achievements,	// 도전과제 진행도 · 상태 · 보상 (서버 표가 원본, BE-96) · 해금 시각 · 새로 열림 (BE-83)
	Rewards unlocked,				// 지금 열린 것: 처음부터 열린 것 + 달성한 도전과제의 보상 (BE-96)
	CollectionView collection,		// 모은 일러스트 · 의상 (BE-83). 기록이 없으면 0 · 빈 배열
	ProfileSettingsValues profileSettings,	// 공개 성향카드 · 프로필카드 아래 성향 · 한 줄 소개(없으면 기본값, v0.52 BE-104)
	LevelStatus level) {			// 레벨 · 직업 (L-1과 같은 값, BE-106)

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static MyPageResponse empty() {
		ProfileDecoView deco = ProfileDecoService.emptyView();
		return new MyPageResponse(null, null, BaselineRetake.NONE, null, List.of(), List.of(), Profile.EMPTY,
			deco.profileDeco(), deco.achievements(), deco.unlocked(), CollectionView.EMPTY, ProfileSettingsValues.DEFAULTS,
			LevelStatus.initial());
	}

	/**
	 * 처음 성향(초심, API 명세 P-5 v0.50). source로 모양이 갈린다 — 화면은 source로 나눠 읽는다.
	 * TEST: 첫 테스트 결과(결과 공통 형식 + source), JUDGMENT: 테스트 전의 첫 판결(judgmentPersonality 모양 + source)
	 */
	public sealed interface InitialPersonality permits InitialTest, InitialJudgment {

		String source();
	}

	/** 첫 테스트 결과(가장 오래된 응시 행). 재응시 · 재심으로 바뀌지 않는다 */
	public record InitialTest(String source, PersonalityTypeResponse type, Map<ValueAxis, Double> axisScores,
		OffsetDateTime testedAt, Integer completedExperienceCount) implements InitialPersonality {

		public static InitialTest from(PersonalityResultResponse result) {
			return new InitialTest("TEST", result.type(), result.axisScores(), result.testedAt(),
				result.completedExperienceCount());
		}
	}

	/** 테스트 전의 첫 판결: 각 사건의 첫 회차 체험을 완료 순서로 처음 3건 평균. 유형이 있을 때만 만든다 */
	public record InitialJudgment(String source, PersonalityTypeResponse type, Map<ValueAxis, Double> axisScores,
		int experienceCount) implements InitialPersonality {

		public InitialJudgment(PersonalityTypeResponse type, Map<ValueAxis, Double> axisScores, int experienceCount) {
			this("JUDGMENT", type, axisScores, experienceCount);
		}
	}

	/**
	 * 성향카드 재심(API 명세 P-5 v0.50). result: 재심 결과(결과 공통 형식, 없으면 null).
	 * available: 첫 테스트가 있고 아직 재심을 하지 않았을 때만 true
	 */
	public record BaselineRetake(boolean available, PersonalityResultResponse result) {

		public static final BaselineRetake NONE = new BaselineRetake(false, null);
	}

	/** 프로필 수치. attendanceDays: 누적 출석 일수(BE-97, 기록이 없으면 0) */
	public record Profile(int attendanceDays) {

		static final Profile EMPTY = new Profile(0);
	}

	/**
	 * 현재 판결 성향: 첫 회차 체험 중 완료 순서로 최근 3건의 평균 (성향 산식 4장 v0.8, API 명세 v0.44).
	 * type은 네 축 모두 값이 있을 때만 있다. experienceCount는 평균에 쓴 체험 수(최대 3)
	 */
	public record JudgmentPersonality(Map<ValueAxis, Double> axisScores, PersonalityTypeResponse type,
		int experienceCount) {
	}

	/**
	 * 성향 변화 흐름의 한 점: 사건의 첫 회차 체험별 축 점수 (자료 없는 축은 null).
	 * type은 이 체험 한 건의 유형으로, judgmentPersonality.type과 같은 규칙이다(네 축 모두 값이 있을 때만, BE-100)
	 */
	public record HistoryItem(Long caseId, String title, OffsetDateTime completedAt, Map<ValueAxis, Double> axisScores,
		PersonalityTypeResponse type) {

		public static HistoryItem of(Experience experience, Instant fallbackCompletedAt, Map<ValueAxis, Double> axisScores,
			PersonalityTypeResponse type) {
			Instant completedAt = experience.getCompletedAt() != null ? experience.getCompletedAt() : fallbackCompletedAt;
			return new HistoryItem(experience.getLegalCase().getId(), experience.getLegalCase().getTitle(),
				kst(completedAt), axisScores, type);
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
