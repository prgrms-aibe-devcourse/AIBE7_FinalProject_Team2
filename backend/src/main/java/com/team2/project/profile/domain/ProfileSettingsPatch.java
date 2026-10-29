package com.team2.project.profile.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * P-8 부분 저장 요청 (API 명세 v0.52, BE-104). 보낸 칸만 Optional에 값이 있다.
 * <p>요청 본문을 JsonNode로 받아 직접 본다 — "보내지 않음"과 null을 구분하고, 모르는 칸 · 문자열이 아닌 값을 400으로 막기 위해서다.
 * <ul>
 *   <li>본문이 객체가 아니거나, 칸이 하나도 없거나, 모르는 칸이 있으면 400</li>
 *   <li>shownCard · profileCardType: initial · retake · current 중 하나(문자열). null이면 400</li>
 *   <li>cardLine: type · signature · job 중 하나(문자열). null이면 400</li>
 *   <li>cardLevel: boolean. 문자열 "true" 같은 값 · null이면 400</li>
 *   <li>bio: 문자열. 앞뒤 공백을 뺀 뒤 0 ~ 40자, 줄바꿈 · 제어 문자(탭 · NUL 등)가 있으면 400. 빈 문자열은 소개 지우기. null이면 400</li>
 * </ul>
 * 아직 재심이 없는데 retake를 골라도 받는다 — 화면이 고른 칸이 비면 대신 고르는 규칙을 이미 쓴다(shownSlot · selectedChoice)
 */
public record ProfileSettingsPatch(Optional<String> shownCard, Optional<String> profileCardType, Optional<String> bio,
		Optional<String> cardLine, Optional<Boolean> cardLevel) {

	private static final Set<String> FIELDS = Set.of("shownCard", "profileCardType", "bio", "cardLine", "cardLevel");

	public static ProfileSettingsPatch parse(JsonNode body) {
		if (body == null || !body.isObject() || body.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);	// 객체가 아니거나 바꿀 칸이 하나도 없다
		}
		body.properties().forEach(entry -> {
			if (!FIELDS.contains(entry.getKey())) {
				throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, entry.getKey());
			}
		});
		return new ProfileSettingsPatch(slot(body, "shownCard"), slot(body, "profileCardType"), bio(body), cardLine(body), cardLevel(body));
	}

	private static Optional<String> slot(JsonNode body, String field) {
		if (!body.has(field)) {
			return Optional.empty();
		}
		JsonNode value = body.get(field);
		if (!value.isString() || !ProfileSettingsValues.SLOTS.contains(value.asString())) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, field);
		}
		return Optional.of(value.asString());
	}

	private static Optional<String> cardLine(JsonNode body) {
		if (!body.has("cardLine")) {
			return Optional.empty();
		}
		JsonNode value = body.get("cardLine");
		if (!value.isString() || !ProfileSettingsValues.CARD_LINES.contains(value.asString())) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "cardLine");
		}
		return Optional.of(value.asString());
	}

	private static Optional<Boolean> cardLevel(JsonNode body) {
		if (!body.has("cardLevel")) {
			return Optional.empty();
		}
		JsonNode value = body.get("cardLevel");
		if (!value.isBoolean()) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "cardLevel");
		}
		return Optional.of(value.asBoolean());
	}

	private static Optional<String> bio(JsonNode body) {
		if (!body.has("bio")) {
			return Optional.empty();
		}
		JsonNode value = body.get("bio");
		if (!value.isString()) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "bio");
		}
		String bio = value.asString().strip();
		if (bio.codePointCount(0, bio.length()) > ProfileSettingsValues.BIO_MAX || bio.codePoints().anyMatch(ProfileSettingsPatch::isForbidden)) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "bio");
		}
		return Optional.of(bio);
	}

	/**
	 * 한 줄 소개에 둘 수 없는 글자: 제어 문자 전체(줄바꿈 · 탭 · NUL · U+0085 등)와 유니코드 줄 · 문단 구분자(U+2028 · U+2029).
	 * NUL은 PostgreSQL 문자열에 저장할 수 없어 걸러 내지 않으면 400이 아니라 500이 된다(PR #251 CodeRabbit 리뷰).
	 */
	private static boolean isForbidden(int c) {
		return Character.isISOControl(c) || c == '\u2028' || c == '\u2029';
	}
}
