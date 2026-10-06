package com.team2.project.judgment.service;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.service.MyExperienceService;
import com.team2.project.judgment.domain.ComparisonMatrix;
import com.team2.project.judgment.domain.FinalJudgmentText;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.MatrixRow;
import com.team2.project.judgment.domain.PreToFinalDirection;
import com.team2.project.judgment.domain.RuleSentences;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.dto.ComparisonResponse;
import com.team2.project.judgment.dto.ComparisonResponse.PreJudgmentView;
import com.team2.project.judgment.dto.ComparisonResponse.PreToFinal;
import com.team2.project.judgment.dto.JudgmentView;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 14. 세 판결 비교 (S-09)
 * 비교를 공개한 뒤(COMPLETED)에만 볼 수 있다. 조회는 상태를 바꾸지 않는다 (API 명세 2장).
 * 공개 사건에 검수한 AI · 재판부 판결이 없으면 RevealService가 상태를 옮기기 전에 막으므로,
 * 여기서 조회에 실패하면 데이터 문제다 (JudgmentResultService와 같은 불변식).
 */
@Service
@RequiredArgsConstructor
public class ComparisonService {

	private final MyExperienceService myExperienceService;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final JudgmentViewAssembler assembler;

	@Transactional(readOnly = true)
	public ComparisonResponse getComparison(Long caseId, Optional<UUID> anonymousId) {
		Experience experience = myExperienceService.getMyExperienceAtLeast(caseId, anonymousId,
			ExperienceStatus.COMPLETED);

		Map<SubjectType, Judgment> published = judgmentRepository.findPublishedJudgments(caseId).stream()
			.collect(Collectors.toMap(Judgment::getSubjectType, Function.identity()));
		Judgment court = required(published, caseId, SubjectType.COURT);
		Judgment ai = required(published, caseId, SubjectType.AI);
		Judgment mine = userFinal(experience);
		Judgment pre = preJudgment(experience);

		Map<Long, List<JudgmentFactor>> finalFactors = factorsOf(mine, ai, court);
		List<JudgmentFactor> preFactors = judgmentFactorRepository.findAllByJudgmentIds(List.of(pre.getId()));

		Map<SubjectType, JudgmentView> judgments = Map.of(
			SubjectType.USER, view(mine, finalFactors),
			SubjectType.AI, view(ai, finalFactors),
			SubjectType.COURT, view(court, finalFactors));

		List<MatrixRow> matrix = ComparisonMatrix.build(
			finalFactors.getOrDefault(mine.getId(), List.of()),
			finalFactors.getOrDefault(ai.getId(), List.of()),
			finalFactors.getOrDefault(court.getId(), List.of()));

		PreToFinal preToFinal = preToFinal(pre, preFactors, mine);

		return new ComparisonResponse(preToFinal, judgments, matrix, RuleSentences.from(matrix), false);
	}

	private PreToFinal preToFinal(Judgment pre, List<JudgmentFactor> preFactors, Judgment mine) {
		PreToFinalDirection direction = PreToFinalDirection.of(pre.getRangeOption(), mine);
		PreJudgmentView preJudgmentView = new PreJudgmentView(
			pre.getRangeOption().getId(),
			pre.getRangeOption().getLabel(),
			preFactors.stream().map(factor -> factor.getFactor().getId()).toList());
		return new PreToFinal(preJudgmentView, FinalJudgmentText.of(mine), direction, summaryText(direction));
	}

	private String summaryText(PreToFinalDirection direction) {
		return switch (direction) {
			case HEAVIER -> "사건을 모두 확인한 뒤, 처음 생각보다 무거운 판결을 내렸어요.";
			case SAME -> "사건을 모두 확인한 뒤, 처음 생각한 정도로 판결을 내렸어요.";
			case LIGHTER -> "사건을 모두 확인한 뒤, 처음 생각보다 가벼운 판결을 내렸어요.";
		};
	}

	private JudgmentView view(Judgment judgment, Map<Long, List<JudgmentFactor>> factors) {
		return assembler.toView(judgment, factors.getOrDefault(judgment.getId(), List.of()));
	}

	/** 여러 판단의 요소 기록을 한 번에 읽어 판단별로 나눈다 (표시 순서 유지, JudgmentResultService와 같은 패턴) */
	private Map<Long, List<JudgmentFactor>> factorsOf(Judgment... judgments) {
		List<Long> ids = Arrays.stream(judgments).map(Judgment::getId).toList();
		return judgmentFactorRepository.findAllByJudgmentIds(ids).stream()
			.collect(Collectors.groupingBy(factor -> factor.getJudgment().getId()));
	}

	private Judgment required(Map<SubjectType, Judgment> published, Long caseId, SubjectType subjectType) {
		Judgment judgment = published.get(subjectType);
		if (judgment == null) {
			throw JudgmentResultService.missingJudgment(caseId, subjectType);
		}
		return judgment;
	}

	/** 비교를 공개한 체험이므로 최종 판결이 반드시 있다 */
	private Judgment userFinal(Experience experience) {
		return judgmentRepository.findUserFinalJudgment(experience.getId())
			.orElseThrow(() -> new IllegalStateException(
				"체험 " + experience.getId() + "의 최종 판결이 없습니다."));
	}

	/** 판결을 확정한 체험이므로 사전 판단도 반드시 있다(API 5가 먼저 저장한다) */
	private Judgment preJudgment(Experience experience) {
		return judgmentRepository.findPreJudgment(experience.getId())
			.orElseThrow(() -> new IllegalStateException(
				"체험 " + experience.getId() + "의 사전 판단이 없습니다."));
	}
}
