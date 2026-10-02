package com.team2.project.judgment.domain;

import com.team2.project.experience.domain.Experience;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.judgment.domain.InvalidJudgmentException.Reason;
import com.team2.project.legalcase.domain.SentenceRangeOption;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 판단 (사용자 사전 판단 · 최종 판결, AI 판결, 재판부 판결을 한 테이블에, ERD 결정 #2)
 * - 사용자 판단은 정적 팩토리(userPre, userFinal)로만 만든다. 수정 메서드는 두지 않는다 (제출 · 확정 후 수정 불가)
 * - AI · COURT 판결은 팀이 검수 후 SQL로 등록한다
 * - 조회는 JudgmentRepository의 목적별 메서드로만 한다 (ERD 7장)
 */
@Entity
@Table(name = "judgment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Judgment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "case_id")
	private LegalCase legalCase;

	@Enumerated(EnumType.STRING)
	private SubjectType subjectType;

	@Enumerated(EnumType.STRING)
	private Timing timing;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "experience_id")
	private Experience experience;			// USER일 때만

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "range_option_id")
	private SentenceRangeOption rangeOption;	// PRE일 때만

	@Enumerated(EnumType.STRING)
	private PenaltyType penaltyType;		// 법정형에서 고른 형벌 (FINAL 필수)

	@Enumerated(EnumType.STRING)
	private PenaltyType reducedTo;			// 감경 후 형벌 (형벌 종류가 바뀌는 감경만)

	private Integer prisonMonths;			// 징역 개월

	private Long fineAmount;				// 벌금 (원)

	private Integer suspensionMonths;		// 집행유예 기간 (개월)

	@JdbcTypeCode(SqlTypes.JSON)
	private List<ExtraDisposition> extraDispositions;	// 부가 처분 (주로 COURT)

	private String summary;					// 판결 카드 한 줄 요약 (AI · COURT)

	private String reasoning;				// 판결 이유 요약 (AI · COURT)

	private String plainExplanation;		// 쉬운 설명 (COURT)

	private String excerpt;					// 판결문 발췌 (COURT, 비식별화 적용)

	private String freeOpinion;				// 자유 의견 (USER, 확장, 비교 대상 아님)

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> referenceTags;		// 참고 자료 태그 (AI)

	private boolean isPublished;				// USER는 항상 true, AI · COURT는 검수 후 true

	@CreationTimestamp
	private Instant createdAt;

	/** 사용자 사전 판단 (형량 구간만, 형벌 값 없음) */
	public static Judgment userPre(Experience experience, SentenceRangeOption rangeOption) {
		Judgment judgment = new Judgment();
		judgment.legalCase = experience.getLegalCase();
		judgment.subjectType = SubjectType.USER;
		judgment.timing = Timing.PRE;
		judgment.experience = experience;
		judgment.rangeOption = rangeOption;
		judgment.isPublished = true;
		return judgment;
	}

	/**
	 * 사용자 최종 판결. 값 조합은 DB CHECK와 같은 규칙으로 여기서 먼저 막는다.
	 * 선고 가능 범위 · 집행유예 법정 조건(1 ~ 5년 등) 검증은 서비스에서 한다.
	 */
	public static Judgment userFinal(Experience experience, PenaltyType penaltyType, PenaltyType reducedTo,
		Integer prisonMonths, Long fineAmount, Integer suspensionMonths, String freeOpinion) {
		validateFinalValues(penaltyType, reducedTo, prisonMonths, fineAmount, suspensionMonths);
		Judgment judgment = new Judgment();
		judgment.legalCase = experience.getLegalCase();
		judgment.subjectType = SubjectType.USER;
		judgment.timing = Timing.FINAL;
		judgment.experience = experience;
		judgment.penaltyType = penaltyType;
		judgment.reducedTo = reducedTo;
		judgment.prisonMonths = prisonMonths;
		judgment.fineAmount = fineAmount;
		judgment.suspensionMonths = suspensionMonths;
		judgment.freeOpinion = freeOpinion;
		judgment.isPublished = true;
		return judgment;
	}

	/** 최종 선고 형벌: reduced_to가 있으면 그 값, 없으면 penalty_type (ERD v1.4) */
	public PenaltyType getFinalPenaltyType() {
		return reducedTo != null ? reducedTo : penaltyType;
	}

	/** 집행유예 선고 여부. 형량 비교에서 실형과 다른 단계로 본다 (PenaltyDifference) */
	public boolean isSuspended() {
		return suspensionMonths != null;
	}

	/** jsonb 배열 컬럼은 비어 있으면 NULL로 들어온다. 응답에서 []로 내려가도록 여기서 한 번만 맞춘다 */
	public List<ExtraDisposition> getExtraDispositions() {
		return extraDispositions == null ? List.of() : extraDispositions;
	}

	/** 참고 자료 태그 (AI). 값이 없으면 빈 배열 (API 10 references) */
	public List<String> getReferenceTags() {
		return referenceTags == null ? List.of() : referenceTags;
	}

	/** DB CHECK(chk_judgment_reduced_to · penalty_values · death_life_no_suspension · positive_values)와 같은 규칙 */
	private static void validateFinalValues(PenaltyType penaltyType, PenaltyType reducedTo,
		Integer prisonMonths, Long fineAmount, Integer suspensionMonths) {
		if (penaltyType == null) {
			throw new InvalidJudgmentException(Reason.MISSING_PENALTY_TYPE, "최종 판결에는 형벌 종류가 필요합니다.");
		}
		if (reducedTo != null && !penaltyType.canReduceTo(reducedTo)) {
			throw new InvalidJudgmentException(Reason.INVALID_REDUCTION,
				penaltyType + "은(는) " + reducedTo + "(으)로 감경할 수 없습니다.");
		}
		// 집행유예는 형벌 종류별 값 검사보다 먼저 본다. 사형 · 무기를 골랐으면 감경 여부와 관계없이 INVALID_SUSPENSION
		if (penaltyType.isDeathOrLife() && suspensionMonths != null) {
			throw new InvalidJudgmentException(Reason.SUSPENSION_NOT_ALLOWED,
				"사형 · 무기징역을 고르면 감경해도 집행유예를 적용할 수 없습니다.");
		}
		PenaltyType finalType = reducedTo != null ? reducedTo : penaltyType;
		switch (finalType) {
			case PRISON -> require(prisonMonths != null && fineAmount == null, "징역은 개월 수만 입력합니다.");
			case FINE -> require(fineAmount != null && prisonMonths == null, "벌금은 금액만 입력합니다.");
			case DEATH, LIFE -> require(prisonMonths == null && fineAmount == null,
				"사형 · 무기징역은 형량을 입력하지 않습니다.");
		}
		require(isPositiveOrNull(prisonMonths) && isPositiveOrNull(fineAmount) && isPositiveOrNull(suspensionMonths),
			"형량 값은 0보다 커야 합니다.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new InvalidJudgmentException(Reason.INVALID_TERM_VALUES, message);
		}
	}

	private static boolean isPositiveOrNull(Number value) {
		return value == null || value.longValue() > 0;
	}
}
