package com.team2.project.admin.caseimport.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CaseSection;
import com.team2.project.legalcase.domain.CrimeType;
import com.team2.project.legalcase.domain.Difficulty;
import com.team2.project.legalcase.domain.Factor;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyRule;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.domain.RevealStage;
import com.team2.project.legalcase.domain.SectionStage;
import com.team2.project.legalcase.domain.SentencingGuideline;
import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.legalcase.domain.ValueAxisVotes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 사건 입력 JSON → 사건 콘텐츠 엔티티 (BE-93 2차).
 *
 * <p>파이썬 `case_seed_sql.py`가 만들던 SQL을 엔티티 생성으로 옮긴 것이다. SQL 문자열을 만들지 않으므로
 * 이스케이프 · 달러 인용 같은 부분은 옮기지 않고, <b>"어떤 JSON 값이 어떤 컬럼으로 가는가"와 검증만</b> 옮겼다.
 *
 * <p>두 파일을 함께 읽는다 — 사건 입력(`case.json`)과 검증 보고(`report.json`). 요약어 · 사전 라벨 ·
 * 가치관 축은 보고 쪽 `factorExtras`에 있고 `factorId`로 맞춘다.
 */
@Component
public class CaseContentMapper {

	/** 판단 요소의 `factorId`는 `factor.display_order`로 그대로 들어간다 (아래 {@link #factors} 참고) */
	public LegalCase toCase(JsonNode caseContent, JsonNode report, LocalDate incidentDate,
			SentencingGuideline guideline) {
		JsonNode listing = caseContent.get("listing");
		if (listing == null || !listing.isObject()) {
			throw Json.invalid("case.listing");
		}
		JsonNode recommended = caseContent.get("recommended");
		return LegalCase.draft(
			Json.requiredText(caseContent, "title", "case.title"),
			Json.requiredEnum(CrimeType.class, Json.text(caseContent, "crimeType"), "case.crimeType"),
			Json.requiredText(caseContent, "chargeName", "case.chargeName"),
			Json.requiredText(listing, "shortIntro", "case.listing.shortIntro"),
			Json.stringArray(listing, "keywords"),
			Json.enumValue(Difficulty.class, Json.text(listing, "difficulty"), "case.listing.difficulty"),
			Json.integer(listing, "estimatedMinutes"),
			Json.requiredText(caseContent, "overview", "case.overview"),
			Json.stringArray(report, "deidentifiedItems"),
			Json.requiredText(caseContent, "appliedLaw", "case.appliedLaw"),
			Json.requiredText(caseContent, "statutoryPenaltyText", "case.statutoryPenaltyText"),
			Json.integer(recommended, "minMonths"),
			Json.integer(recommended, "maxMonths"),
			Json.text(recommended, "basis"),
			guideline,
			incidentDate);
	}

	/** 섹션. `display_order`는 배열 순서(1부터)다 — 화면이 이 순서로 보여 준다 */
	public List<CaseSection> sections(LegalCase legalCase, JsonNode caseContent) {
		List<JsonNode> items = Json.array(caseContent, "sections");
		if (items.isEmpty()) {
			throw Json.invalid("case.sections");
		}
		List<CaseSection> sections = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			String path = "case.sections[" + i + "]";
			sections.add(CaseSection.of(legalCase,
				Json.requiredEnum(SectionStage.class, Json.text(item, "stage"), path + ".stage"),
				Json.requiredText(item, "sectionType", path + ".sectionType"),
				Json.text(item, "title"),
				Json.text(item, "content"),
				sectionData(item),
				i + 1));
		}
		return sections;
	}

	/** `data`는 섹션에 따라 [{label,value}] · [{term,desc}] 꼴이라 모양을 따지지 않고 그대로 담는다 */
	private static List<Object> sectionData(JsonNode item) {
		JsonNode data = item.get("data");
		if (data == null || data.isNull() || !data.isArray()) {
			return null;
		}
		List<Object> values = new ArrayList<>();
		data.forEach(entry -> values.add(Json.toMap(entry)));
		return values;
	}

	/**
	 * 형벌 규칙. `display_order`는 배열 순서(1부터)이고, 배열은 이미 DEATH → LIFE → PRISON → FINE 순이다.
	 *
	 * <p>선고 가능 범위 산출 근거(`allowedBasis`)만 사건 입력이 아니라 <b>검증 보고</b>에 있다
	 * (`report.penaltyRuleBasis[형벌]`). AI에 넣는 입력에서 빼려고 떼어 둔 값이다.
	 */
	public List<PenaltyRule> penaltyRules(LegalCase legalCase, JsonNode caseContent, JsonNode report) {
		List<JsonNode> items = Json.array(caseContent, "penaltyRules");
		if (items.isEmpty()) {
			throw Json.invalid("case.penaltyRules");
		}
		JsonNode basis = report == null ? null : report.get("penaltyRuleBasis");
		List<PenaltyRule> rules = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			String path = "case.penaltyRules[" + i + "]";
			PenaltyType penaltyType =
				Json.requiredEnum(PenaltyType.class, Json.text(item, "penaltyType"), path + ".penaltyType");
			boolean suspensionAllowed = Json.bool(item, "suspensionAllowed");
			// 사형 · 무기징역에는 집행유예를 붙일 수 없다 (DB CHECK와 같은 조건을 먼저 본다)
			if (penaltyType.isDeathOrLife() && suspensionAllowed) {
				throw Json.invalid(path + ".suspensionAllowed");
			}
			rules.add(PenaltyRule.of(legalCase, penaltyType,
				Json.longValue(item, "statutoryMin"),
				Json.longValue(item, "statutoryMax"),
				Json.requiredLong(item, "allowedMin", path + ".allowedMin"),
				Json.requiredLong(item, "allowedMax", path + ".allowedMax"),
				Json.text(basis, penaltyType.name()),
				suspensionAllowed,
				i + 1));
		}
		return rules;
	}

	/**
	 * 판단 요소.
	 *
	 * <p><b>`display_order`에 배열 순서가 아니라 `factorId`를 넣는다.</b> AI · 재판부 판결의 작용 요소가
	 * 이 번호로 요소를 찾으므로(파이프라인의 세 생성기가 모두 같은 규칙) 바꾸면 판결과 요소의 연결이 끊긴다.
	 *
	 * <p>요약어 · 사전 라벨 · 가치관 축은 사건 입력이 아니라 검증 보고의 `factorExtras`에 있다.
	 */
	public List<Factor> factors(LegalCase legalCase, JsonNode caseContent, JsonNode report, JsonNode axis) {
		List<JsonNode> items = Json.array(caseContent, "factors");
		if (items.isEmpty()) {
			throw Json.invalid("case.factors");
		}
		Map<Integer, JsonNode> extras = byFactorId(Json.array(report, "factorExtras"));
		// 가치관 축 투표는 축 단계 결과에 따로 있다. 파이프라인은 적재 단계에서 둘을 합치는데
		// 그 단계를 껐으므로(COMMON-50) 여기서 합친다
		Map<Integer, JsonNode> votes = byFactorId(Json.array(axis, "factors"));
		List<Factor> factors = new ArrayList<>();
		boolean hasOverview = false;
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			String path = "case.factors[" + i + "]";
			Integer factorId = Json.integer(item, "factorId");
			if (factorId == null) {
				throw Json.invalid(path + ".factorId");
			}
			JsonNode extra = extras.get(factorId);
			if (extra == null) {
				// 요약어가 없으면 판결 카드 한 줄 요약을 만들 수 없다
				throw Json.invalid("report.factorExtras[factorId=" + factorId + "]");
			}
			RevealStage revealStage =
				Json.requiredEnum(RevealStage.class, Json.text(item, "revealStage"), path + ".revealStage");
			hasOverview |= revealStage == RevealStage.OVERVIEW;
			JsonNode vote = votes.get(factorId);
			// 투표를 했으면 그 결과가 추출기 값보다 낫다 (여러 번 물어 다수결로 정한 값이다)
			String axisValue = vote != null && Json.text(vote, "valueAxis") != null
				? Json.text(vote, "valueAxis") : Json.text(extra, "valueAxis");
			factors.add(Factor.of(legalCase,
				Json.requiredText(item, "label", path + ".label"),
				Json.text(extra, "preLabel"),
				revealStage,
				Json.requiredText(extra, "summaryTag", "report.factorExtras[factorId=" + factorId + "].summaryTag"),
				Json.enumValue(ValueAxis.class, axisValue, "axis.factors[factorId=" + factorId + "].valueAxis"),
				valueAxisVotes(vote),
				factorId));
		}
		if (!hasOverview) {
			// 사전 판단 화면에 쓸 요소가 없으면 사건을 공개할 수 없다 (PublishReadiness NO_OVERVIEW_FACTOR)
			throw new BusinessException(ErrorCode.IMPORT_RESULT_INVALID,
				"사전 판단에 쓸 판단 요소(OVERVIEW)가 없어 사건을 만들 수 없습니다.");
		}
		return factors;
	}

	private static Map<Integer, JsonNode> byFactorId(List<JsonNode> items) {
		Map<Integer, JsonNode> map = new HashMap<>();
		for (JsonNode item : items) {
			Integer id = Json.integer(item, "factorId");
			if (id != null) {
				map.put(id, item);
			}
		}
		return map;
	}

	/** 투표 기록. 모양이 맞지 않으면 적재를 막지 않고 비워 둔다 — 축 값 자체는 이미 넣었다 */
	private static ValueAxisVotes valueAxisVotes(JsonNode vote) {
		JsonNode votes = vote == null ? null : vote.get("valueAxisVotes");
		if (votes == null || !votes.isObject()) {
			return null;
		}
		Map<String, Object> counts = Json.toMap(votes.get("counts"));
		if (counts == null) {
			return null;
		}
		Map<String, Integer> tally = new java.util.LinkedHashMap<>();
		counts.forEach((key, value) -> tally.put(key, value instanceof Number number ? number.intValue() : 0));
		Integer runs = Json.integer(votes, "runs");
		return new ValueAxisVotes(runs == null ? 0 : runs, tally, Json.bool(votes, "needsReview"),
			Json.integer(votes, "requestedRuns"));
	}
}
