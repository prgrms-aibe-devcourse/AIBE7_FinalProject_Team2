package com.team2.project.admin.caseimport.load;

import tools.jackson.databind.JsonNode;
import com.team2.project.judgment.domain.Direction;
import com.team2.project.judgment.domain.ExtraDisposition;
import com.team2.project.judgment.domain.ExtraDispositionType;
import com.team2.project.judgment.domain.Judgment;
import com.team2.project.judgment.domain.JudgmentFactor;
import com.team2.project.judgment.domain.SubjectType;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 재판부 · AI 판결 JSON → 판결 엔티티 (BE-93 2차).
 *
 * <p>파이썬 `court_seed_sql.py` · `to_seed_sql.py`를 옮긴 것이다. 두 생성기는 쓰는 필드가 다르지만
 * (재판부는 쉬운 설명 · 판결문 발췌 · 부가 처분이 있고, AI는 참고 자료 태그가 있다) 판결을 만드는 뼈대는
 * 같아서 한 곳에 둔다.
 *
 * <p><b>둘 다 공개하지 않은 상태로 만든다.</b> JSON에 `isPublished`가 있어도 무시한다 — 관리자가 A-9에서
 * 승인해야 공개 후보가 된다 (파이썬도 같은 이유로 항상 false를 쓴다).
 */
@Component
public class JudgmentMapper {

	/** 재판부 판결은 네 가지 글이 모두 있어야 한다 (파이썬 court_seed_sql.REQUIRED_TEXT와 같다) */
	public Judgment courtJudgment(LegalCase legalCase, JsonNode courtDraft) {
		JsonNode judgment = courtDraft == null ? null : courtDraft.get("judgment");
		if (judgment == null || !judgment.isObject()) {
			throw Json.invalid("courtDraft.judgment");
		}
		String path = "courtDraft.judgment";
		return Judgment.candidate(legalCase, SubjectType.COURT,
			Json.requiredEnum(PenaltyType.class, Json.text(judgment, "penaltyType"), path + ".penaltyType"),
			Json.enumValue(PenaltyType.class, Json.text(judgment, "reducedTo"), path + ".reducedTo"),
			Json.integer(judgment, "prisonMonths"),
			Json.longValue(judgment, "fineAmount"),
			Json.integer(judgment, "suspensionMonths"),
			extraDispositions(judgment),
			Json.requiredText(judgment, "summary", path + ".summary"),
			Json.requiredText(judgment, "reasoning", path + ".reasoning"),
			Json.requiredText(judgment, "plainExplanation", path + ".plainExplanation"),
			Json.requiredText(judgment, "excerpt", path + ".excerpt"),
			List.of());	// 참고 자료 태그는 AI 판결에만 있다
	}

	/** AI 판결. 쉬운 설명 · 판결문 발췌 · 부가 처분은 재판부에만 있는 값이라 비운다 */
	public Judgment aiJudgment(LegalCase legalCase, JsonNode output) {
		if (output == null || !output.isObject()) {
			throw Json.invalid("judgment");
		}
		return Judgment.candidate(legalCase, SubjectType.AI,
			Json.requiredEnum(PenaltyType.class, Json.text(output, "penaltyType"), "judgment.penaltyType"),
			Json.enumValue(PenaltyType.class, Json.text(output, "reducedTo"), "judgment.reducedTo"),
			Json.integer(output, "prisonMonths"),
			Json.longValue(output, "fineAmount"),
			Json.integer(output, "suspensionMonths"),
			List.of(),	// 부가 처분은 재판부 판결에만 있다
			Json.requiredText(output, "summary", "judgment.summary"),
			Json.requiredText(output, "reasoning", "judgment.reasoning"),
			null,
			null,
			Json.stringArray(output, "referenceTags"));
	}

	/**
	 * 작용 요소. 판결이 고른 요소만 행을 만든다 — 빠진 요소는 "고려하지 않았다"는 뜻이다.
	 *
	 * <p>요소를 찾는 키는 <b>`factorId`</b>다. 적재할 때 `factor.display_order`에 같은 값을 넣었기 때문에
	 * (CaseContentMapper 참고) 둘이 맞아떨어진다. 판결이 사건에 없는 번호를 가리키면 검증 오류로 막는다 —
	 * 그대로 두면 작용 요소가 조용히 사라진 판결이 만들어진다.
	 *
	 * @param arrayField 재판부는 `judgmentFactors`, AI는 `factors`
	 * @param evidenceField 재판부는 `evidence`, AI는 `reason`
	 */
	public List<JudgmentFactor> judgmentFactors(Judgment judgment, List<Factor> factors, JsonNode source,
			String arrayField, String evidenceField, String path) {
		Map<Integer, Factor> byDisplayOrder = factors.stream()
			.collect(java.util.stream.Collectors.toMap(Factor::getDisplayOrder, Function.identity()));
		List<JsonNode> items = new ArrayList<>(Json.array(source, arrayField));
		// 번호 오름차순으로 넣는다 (파이썬 생성기와 같은 순서 — 화면이 이 순서로 보여 준다)
		items.sort(Comparator.comparing(item -> {
			Integer id = Json.integer(item, "factorId");
			return id == null ? Integer.MAX_VALUE : id;
		}));
		List<JudgmentFactor> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			String itemPath = path + "." + arrayField + "[" + i + "]";
			Integer factorId = Json.integer(item, "factorId");
			if (factorId == null) {
				throw Json.invalid(itemPath + ".factorId");
			}
			Factor factor = byDisplayOrder.get(factorId);
			if (factor == null) {
				// 사건에 없는 판단 요소를 가리킨다
				throw Json.invalid(itemPath + ".factorId");
			}
			result.add(JudgmentFactor.forLoad(judgment, factor,
				Json.requiredEnum(Direction.class, Json.text(item, "direction"), itemPath + ".direction"),
				Json.text(item, evidenceField)));
		}
		return result;
	}

	private static List<ExtraDisposition> extraDispositions(JsonNode judgment) {
		List<ExtraDisposition> dispositions = new ArrayList<>();
		List<JsonNode> items = Json.array(judgment, "extraDispositions");
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			String path = "courtDraft.judgment.extraDispositions[" + i + "]";
			dispositions.add(new ExtraDisposition(
				Json.requiredEnum(ExtraDispositionType.class, Json.text(item, "type"), path + ".type"),
				Json.text(item, "value")));
		}
		return dispositions;
	}
}
