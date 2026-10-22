package com.team2.project.profile.domain;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * P-8 부분 저장 요청 (API 명세 v0.51, BE-104). 보낸 칸만 Optional에 값이 있다.
 * <p>요청 본문을 JsonNode로 받아 직접 본다 — "보내지 않음"과 null을 구분하고, 모르는 칸 · 문자열이 아닌 값을 400으로 막기 위해서다.
 * <ul>
 *   <li>본문이 객체가 아니거나, 칸이 하나도 없거나, 모르는 칸이 있으면 400</li>
 *   <li>shownCard · profileCardType: initial · retake · current 중 하나(문자열). null이면 400</li>
 *   <li>bio: 문자열. 앞뒤 공백을 뺀 뒤 0 ~ 40자, 줄바꿈이 있으면 400. 빈 문자열은 소개 지우기. null이면 400</li>
 * </ul>
 * 아직 재심이 없는데 retake를 골라도 받는다 — 화면이 고른 칸이 비면 대신 고르는 규칙을 이미 쓴다(shownSlot · selectedChoice)
 */
public record ProfileSettingsPatch(Optional<String> shownCard, Optional<String> profileCardType, Optional<String> bio) {

	private static final Map<String, Boolean> FIELDS = Map.of("shownCard", true, "profileCardType", true, "bio", true);

	public static ProfileSettingsPatch parse(JsonNode body) {
		if (body == null || !body.isObject() || body.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);	// 객체가 아니거나 바꿀 칸이 하나도 없다
		}
		body.properties().forEach(entry -> {
			if (!FIELDS.containsKey(entry.getKey())) {
				throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, entry.getKey());
			}
		});
		return new ProfileSettingsPatch(slot(body, "shownCard"), slot(body, "profileCardType"), bio(body));
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

	private static Optional<String> bio(JsonNode body) {
		if (!body.has("bio")) {
			return Optional.empty();
		}
		JsonNode value = body.get("bio");
		if (!value.isString()) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "bio");
		}
		String bio = value.asString().strip();
		if (bio.codePointCount(0, bio.length()) > ProfileSettingsValues.BIO_MAX || bio.chars().anyMatch(ProfileSettingsPatch::isLineBreak)) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "bio");
		}
		return Optional.of(bio);
	}

	/** \n · \r와 유니코드 줄 · 문단 구분자 */
	private static boolean isLineBreak(int c) {
		return c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029' || c == '\u0085';
	}
}
