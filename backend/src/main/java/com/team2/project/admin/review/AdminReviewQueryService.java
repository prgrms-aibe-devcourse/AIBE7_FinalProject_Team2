package com.team2.project.admin.review;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse.Candidate;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse.Content;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse.FactorItem;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse.Guideline;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse.PublishReadinessResponse;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse.ScriptSummary;
import com.team2.project.admin.review.dto.AdminCaseDetailResponse.Source;
import com.team2.project.admin.review.dto.AdminCaseListResponse;
import com.team2.project.admin.review.dto.AdminJudgmentResponse;
import com.team2.project.admin.review.dto.AdminSourceTextResponse;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.judgment.domain.AiGeneration;
import com.team2.project.judgment.domain.FinalJudgmentText;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.judgment.repository.AiGenerationRepository;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.judgment.service.JudgmentViewAssembler;
import com.team2.project.legalcase.domain.CaseSource;
import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.RevealStage;
import com.team2.project.legalcase.domain.SentencingGuideline;
import com.team2.project.legalcase.repository.CaseSourceRepository;
import com.team2.project.legalcase.repository.CaseScriptRepository;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 검수 조회 API (API 명세 8-2 A-5 ~ A-8, BE-41 · WBS 7.4.7). 상태를 바꾸지 않는다.
 * 관리자 API는 사건 상태와 관계없이 찾는다(CASE_NOT_FOUND = 사건이 없음). 사용자 판결(USER)은 보여 주지 않는다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReviewQueryService {

	static final Set<CaseStatus> DEFAULT_STATUSES = EnumSet.of(CaseStatus.DRAFT, CaseStatus.REVIEW);
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final AdminCaseListQuery caseListQuery;
	private final LegalCaseRepository caseRepository;
	private final CaseSourceRepository sourceRepository;
	private final FactorRepository factorRepository;
	private final PenaltyRuleRepository penaltyRuleRepository;
	private final JudgmentRepository judgmentRepository;
	private final JudgmentFactorRepository judgmentFactorRepository;
	private final AiGenerationRepository generationRepository;
	private final AdminAccountRepository adminAccountRepository;
	private final CaseScriptRepository scriptRepository;
	private final JudgmentViewAssembler assembler;
	private final AdminAuditLogger auditLogger;

	/** A-5 검수 사건 목록. 최근 활동 순, 쪽 나눔 */
	public AdminCaseListResponse listCases(Set<CaseStatus> statuses, Boolean pending, int page, int size) {
		return new AdminCaseListResponse(caseListQuery.find(statuses, pending, page, size), page, size,
			caseListQuery.count(statuses, pending));
	}

	/** A-6 사건 검수 상세 */
	public AdminCaseDetailResponse getCase(Long caseId) {
		LegalCase legalCase = findCase(caseId);
		List<Factor> factors = factorRepository.findAllByCaseId(caseId);
		List<Judgment> candidates = judgmentRepository.findCandidatesByCaseId(caseId);
		Map<Long, AiGeneration> generations = generationRepository.findAllByJudgment_IdIn(
				candidates.stream().map(Judgment::getId).toList()).stream()
			.collect(Collectors.toMap(generation -> generation.getJudgment().getId(), Function.identity()));

		Map<String, List<Candidate>> byKind = new LinkedHashMap<>();
		byKind.put(SubjectType.AI.name(), candidates(candidates, SubjectType.AI, generations));
		byKind.put(SubjectType.COURT.name(), candidates(candidates, SubjectType.COURT, generations));

		PublishReadiness readiness = readiness(caseId, candidates, factors);

		return new AdminCaseDetailResponse(
			legalCase.getId(),
			legalCase.getStatus(),
			content(legalCase),
			sourceRepository.findAllByCaseId(caseId).stream().map(AdminReviewQueryService::source).toList(),
			factors.stream().map(AdminReviewQueryService::factor).toList(),
			byKind,
			new PublishReadinessResponse(readiness.ready(), readiness.reasons()),
			// 대본은 공개를 막지 않는다. 화면이 경고로 쓰도록 있는지 · 검수 상태 · 공개 가능 여부만 알려 준다
			scriptRepository.findByLegalCaseId(caseId)
				.map(script -> new ScriptSummary(true, script.reviewState(), script.isApprovable()))
				.orElse(ScriptSummary.NONE));
	}

	/**
	 * A-7 원본 판결문 원문. 원본 판결문이라 열람할 때마다 감사 로그를 남긴다(recordRead: 별도 트랜잭션, 실패하면 원문을 내주지 않는다)
	 */
	public AdminSourceTextResponse getSourceText(Long caseId, Long sourceId, AdminPrincipal admin,
		HttpServletRequest request) {
		CaseSource source = sourceRepository.findById(sourceId)
			.filter(found -> found.getLegalCase().getId().equals(caseId))
			.orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
		auditLogger.recordRead(admin, AdminAuditAction.SOURCE_VIEW, AuditTargetType.CASE_SOURCE, sourceId, request);
		return new AdminSourceTextResponse(source.getId(), source.getOriginalText());
	}

	/** A-8 판결 후보 상세. 사용자 판결은 없는 것으로 본다 */
	public AdminJudgmentResponse getJudgment(Long judgmentId) {
		Judgment judgment = judgmentRepository.findById(judgmentId)
			.filter(found -> found.getSubjectType() != SubjectType.USER)
			.orElseThrow(() -> new BusinessException(ErrorCode.JUDGMENT_NOT_FOUND));
		AiGeneration generation = judgment.getSubjectType() == SubjectType.AI
			? generationRepository.findByJudgment_Id(judgmentId).orElse(null) : null;

		// 검수자 · 시각: 관리자 화면에서 한 검수(V16)가 우선이고, 그 전 시드 · 오프라인 검수는 ai_generation 기록을 보여 준다
		String reviewedBy = judgment.getReviewedByAdminId() != null
			? adminAccountRepository.findById(judgment.getReviewedByAdminId()).map(account -> account.getEmail()).orElse(null)
			: generation != null ? generation.getReviewedBy() : null;
		Instant reviewedAt = judgment.getReviewedAt() != null ? judgment.getReviewedAt()
			: generation != null ? generation.getReviewedAt() : null;

		return new AdminJudgmentResponse(
			judgment.getId(),
			judgment.getLegalCase().getId(),
			judgment.getSubjectType(),
			judgment.reviewState(),
			judgment.isPublished(),
			reviewedBy,
			kst(reviewedAt),
			judgment.getReviewNote(),
			AdminJudgmentResponse.Body.of(
				assembler.toView(judgment, judgmentFactorRepository.findAllByJudgmentIds(List.of(judgmentId))),
				judgment.getReferenceTags()),
			generation == null ? null : new AdminJudgmentResponse.Generation(generation.getModelName(),
				generation.getPromptVersion(), generation.getInputSnapshot(), generation.getGenerationReport()));
	}

	/** 공개 조건 (A-6 표시 · A-13 확인이 같은 판정을 쓴다) */
	public PublishReadiness readinessOf(Long caseId) {
		return readiness(caseId, judgmentRepository.findCandidatesByCaseId(caseId), factorRepository.findAllByCaseId(caseId));
	}

	private PublishReadiness readiness(Long caseId, List<Judgment> candidates, List<Factor> factors) {
		return PublishReadiness.of(
			candidates.stream().anyMatch(j -> j.getSubjectType() == SubjectType.AI && j.isPublished()),
			candidates.stream().anyMatch(j -> j.getSubjectType() == SubjectType.COURT && j.isPublished()),
			factors.size(),
			(int) factors.stream().filter(factor -> factor.getRevealStage() == RevealStage.OVERVIEW).count(),
			penaltyRuleRepository.findAllByCaseId(caseId).size());
	}

	private LegalCase findCase(Long caseId) {
		return caseRepository.findById(caseId).orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));
	}

	private static List<Candidate> candidates(List<Judgment> judgments, SubjectType kind,
		Map<Long, AiGeneration> generations) {
		return judgments.stream()
			.filter(judgment -> judgment.getSubjectType() == kind)
			.map(judgment -> new Candidate(
				judgment.getId(),
				judgment.reviewState(),
				judgment.isPublished(),
				FinalJudgmentText.of(judgment),
				generations.containsKey(judgment.getId()) ? generations.get(judgment.getId()).getModelName() : null,
				kst(judgment.getCreatedAt())))
			.toList();
	}

	private static Content content(LegalCase legalCase) {
		SentencingGuideline guideline = legalCase.getGuideline();
		return new Content(
			legalCase.getTitle(),
			legalCase.getCrimeType(),
			legalCase.getChargeName(),
			legalCase.getShortIntro(),
			legalCase.getOverview(),
			legalCase.getKeywords(),
			legalCase.getDifficulty(),
			legalCase.getEstimatedMinutes(),
			legalCase.getThumbnailUrl(),
			legalCase.getAppliedLaw(),
			legalCase.getStatutoryPenaltyText(),
			legalCase.getRecommendedMinMonths(),
			legalCase.getRecommendedMaxMonths(),
			legalCase.getRecommendedBasis(),
			legalCase.getIncidentDate(),
			guideline == null ? null : new Guideline(guideline.getId(), guideline.getVersionName()),
			legalCase.getDeidentifiedItems(),
			legalCase.getCastRoles(),
			legalCase.getSensitiveFlags());
	}

	private static Source source(CaseSource source) {
		return new Source(source.getId(), source.getCourtLevel(), source.getCaseNumber(), source.getCourtName(),
			source.getDecidedAt(), source.isFinal(), source.getSourceOrg(), source.getNote(),
			source.getOriginalText() != null && !source.getOriginalText().isBlank());
	}

	private static FactorItem factor(Factor factor) {
		return new FactorItem(factor.getId(), factor.getDisplayOrder(), factor.getLabel(), factor.getPreLabel(),
			factor.getRevealStage(), factor.getSummaryTag(), factor.getValueAxis(), factor.getValueAxisStatus(),
			factor.getValueAxisVotes(), factor.getSummaryTagCandidates());
	}

	private static OffsetDateTime kst(Instant instant) {
		return instant == null ? null : instant.atZone(KST).toOffsetDateTime();
	}
}
