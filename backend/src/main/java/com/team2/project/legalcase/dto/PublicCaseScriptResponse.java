package com.team2.project.legalcase.dto;

import com.team2.project.legalcase.domain.CaseScript;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 공개 사건 대본 (API 18, BE-109). BE-107 파이프라인이 만들고 BE-108에서 관리자가 승인한
 * 2막 증거조사 대사만 준다 — 나머지 막(1막 해설 · 피고인신문 · 최종의견 등)은 파이프라인 범위 밖이라
 * FE-33이 사건에 상관없는 공통 틀로 채운다(해송님 · 승호님 합의, 2026-10-27, BE-109 Jira 댓글).
 *
 * <p>관리자용 응답({@code admin.review.dto.CaseScriptResponse})과 달리 검수용 값
 * ({@code manual} · {@code editedAt} · {@code validation} · {@code attempts})은 빼고, 화면이
 * 바로 쓰는 값만 내려준다. <b>순서는 배열 순서 그대로가 재생 순서다</b> — 별도 정렬 필드를 두지 않는다.
 *
 * @param cast 배역 {@code {prosecutor, defense}}. {@link CaseScript#getCastRoles()}를 그대로 내려준다
 */
public record PublicCaseScriptResponse(List<EvidenceLine> evidence, Map<String, Object> cast) {

	public static PublicCaseScriptResponse of(CaseScript script, Map<Integer, String> labelsByFactorId) {
		List<EvidenceLine> lines = script.getEvidence().stream().map(line -> EvidenceLine.from(line, labelsByFactorId))
			.toList();
		return new PublicCaseScriptResponse(lines, script.getCastRoles());
	}

	/**
	 * @param side      검사 · 변호인 코드값({@code PROSECUTOR} · {@code DEFENSE}). 다른 공개 API와 형식을
	 *                  맞추려고 코드값으로 준다 — 화면 한글(검사 · 변호인)로 바꾸는 것은 프론트 몫이다
	 * @param character 이 대사를 하는 배역 ({@link #cast}의 값과 같은 코드)
	 * @param card      이 대사가 다루는 증거 카드 문구 (= 사건의 판단 요소 문구). 파이프라인 사건에는 정적
	 *                  증거 카드가 없어 이 문구가 카드 역할을 그대로 한다 — 요소가 지워졌으면 비운다
	 * @param text      법정에서 실제로 하는 대사
	 */
	public record EvidenceLine(int factorId, String side, String character, String card, String text) {

		static EvidenceLine from(Map<String, Object> line, Map<Integer, String> labelsByFactorId) {
			int factorId = factorIdOf(line.get("factorId"));
			return new EvidenceLine(factorId, side(line.get("role")), (String) line.get("character"),
				labelsByFactorId.get(factorId), (String) line.get("text"));
		}

		/** 저장된 JSON의 숫자는 Integer · Long 어느 쪽으로도 올 수 있다 (admin 쪽과 같은 처리) */
		private static int factorIdOf(Object value) {
			return value instanceof Number number ? number.intValue() : -1;
		}

		/** 파이프라인은 role을 소문자로 준다(script_generation.py ROLE_BY_DIRECTION) — 대문자 코드로 바꿔 돌려준다 */
		private static String side(Object role) {
			return role instanceof String value ? value.toUpperCase(Locale.ROOT) : null;
		}
	}
}
