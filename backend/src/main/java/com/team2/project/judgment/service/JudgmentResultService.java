package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.service.MyExperienceService;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.dto.AiJudgmentResponse;
import com.team2.project.judgment.dto.AiJudgmentResponse.DiffFromMine;
import com.team2.project.judgment.dto.CourtJudgmentResponse;
import com.team2.project.judgment.dto.CourtJudgmentResponse.Source;
import com.team2.project.judgment.dto.JudgmentView;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.legalcase.repository.CaseSourceRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판결 결과 조회 (API 10 AI 판결, API 12 실제 판결)
 * 조회는 몇 번을 불러도 상태를 바꾸지 않는다. 상태를 옮기는 것은 공개 요청(API 11 · 13)뿐이다 (API 명세 2장).
 * 공개 단계에 이르지 않았으면 MyExperienceService가 현재 상태를 담은 INVALID_STATE로 거절한다 (API 명세 1-6).
 */
@Service
@RequiredArgsConstructor
public class JudgmentResultService {

	private final MyExperienceService myExperienceService;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final CaseSourceRepository caseSourceRepository;
	private final JudgmentViewAssembler assembler;

	/** API 10. 판결을 확정한 뒤(VERDICT_CONFIRMED 이상)에만 볼 수 있다 */
	@Transactional(readOnly = true)
	public AiJudgmentResponse getAiJudgment(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceAtLeast(caseId, anonymousId,
			ExperienceStatus.VERDICT_CONFIRMED);
		Judgment ai = published(caseId, SubjectType.AI);
		Judgment mine = userFinal(experience);

		Map<Long, List<JudgmentFactor>> factors = factorsOf(ai, mine);
		return new AiJudgmentResponse(
			view(ai, factors),
			view(mine, factors),
			DiffFromMine.between(ai, mine),
			ai.getReferenceTags() == null ? List.of() : ai.getReferenceTags());
	}

	/** API 12. 실제 판결을 공개한 뒤(AI_REVEALED 이상)에만 볼 수 있다 */
	@Transactional(readOnly = true)
	public CourtJudgmentResponse getCourtJudgment(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceAtLeast(caseId, anonymousId,
			ExperienceStatus.AI_REVEALED);
		Judgment court = published(caseId, SubjectType.COURT);
		Judgment ai = published(caseId, SubjectType.AI);
		Judgment mine = userFinal(experience);

		Map<Long, List<JudgmentFactor>> factors = factorsOf(court, ai, mine);
		List<String> deidentifiedItems = experience.getLegalCase().getDeidentifiedItems();
		return new CourtJudgmentResponse(
			view(court, factors),
			view(mine, factors),
			view(ai, factors),
			caseSourceRepository.findFinalByCaseId(caseId)
				.map(source -> new Source(source.getSourceOrg()))
				.orElse(null),
			deidentifiedItems == null ? List.of() : deidentifiedItems);
	}

	private JudgmentView view(Judgment judgment, Map<Long, List<JudgmentFactor>> factors) {
		return assembler.toView(judgment, factors.getOrDefault(judgment.getId(), List.of()));
	}

	/** 여러 판단의 요소 기록을 한 번에 읽어 판단별로 나눈다 (표시 순서 유지) */
	private Map<Long, List<JudgmentFactor>> factorsOf(Judgment... judgments) {
		List<Long> ids = Arrays.stream(judgments).map(Judgment::getId).toList();
		return judgmentFactorRepository.findAllByJudgmentIds(ids).stream()
			.collect(Collectors.groupingBy(factor -> factor.getJudgment().getId()));
	}

	/**
	 * 사건의 공개된 AI · 재판부 판결. 공개 사건이라면 검수를 마친 판결이 반드시 등록돼 있어야 한다.
	 * 없다면 사용자가 고칠 수 없는 데이터 문제이므로 500으로 두고 로그로 남긴다 (API 명세 거절 조건에 없음).
	 */
	private Judgment published(Long caseId, SubjectType subjectType) {
		return judgmentRepository.findPublishedJudgment(caseId, subjectType)
			.orElseThrow(() -> new IllegalStateException(
				"사건 " + caseId + "에 공개된 " + subjectType + " 판결이 없습니다."));
	}

	/** 판결을 확정한 체험이므로 최종 판결이 반드시 있다 */
	private Judgment userFinal(Experience experience) {
		return judgmentRepository.findUserFinalJudgment(experience.getId())
			.orElseThrow(() -> new IllegalStateException(
				"체험 " + experience.getId() + "의 최종 판결이 없습니다."));
	}
}
