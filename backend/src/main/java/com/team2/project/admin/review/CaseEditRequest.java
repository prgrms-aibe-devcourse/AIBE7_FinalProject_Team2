package com.team2.project.admin.review;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.CastRoles;
import com.team2.project.legalcase.domain.Difficulty;
import com.team2.project.legalcase.domain.SensitiveFlag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A-11 사건 내용 수정 본문 (API 명세 A-11 표). "보내지 않은 필드"와 "null을 보낸 필드"를 나눠야 해서
 * 본문을 Map으로 받아 필드마다 직접 검사한다(보내지 않음 = 그대로, null = 지움, 필수 필드는 null 불가).
 * 숫자는 정수만(소수 거절), 문자열 숫자("15")는 허용한다(API 명세 1-1).
 * 문자열은 앞뒤 공백을 자른 값으로 검사 · 저장한다("X"와 "X "가 다른 제목으로 중복 검사를 피하지 않게, #133 리뷰).
 */
record CaseEditRequest(Map<String, Object> changes) {

	/** 썸네일은 http(s) 주소나 서비스 안 경로("/images/…")만. javascript: · data: · //host 같은 값은 거절 (#133 리뷰) */
	private static final Pattern THUMBNAIL_URL = Pattern.compile("(?i)https?://[^\\s/]+(/\\S*)?|/(?!/)\\S*");

	static final Set<String> FIELDS = Set.of("title", "shortIntro", "overview", "keywords", "difficulty",
		"estimatedMinutes", "thumbnailUrl", "recommendedMinMonths", "recommendedMaxMonths", "recommendedBasis",
		"castRoles", "sensitiveFlags");

	/** 본문을 검사해 바꿀 값만 담는다. 값은 검사를 마친 타입(String · List · Map · Difficulty · Integer · null) */
	static CaseEditRequest parse(Map<String, Object> body) {
		if (body == null || body.isEmpty()) {
			throw invalid("body");
		}
		Map<String, Object> changes = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : body.entrySet()) {
			String field = entry.getKey();
			Object value = entry.getValue();
			changes.put(field, switch (field) {
				case "title" -> requiredText(field, value, 100);
				case "shortIntro" -> requiredText(field, value, 200);
				case "overview" -> requiredText(field, value, Integer.MAX_VALUE);
				case "keywords" -> keywords(value);
				case "difficulty" -> value == null ? null : difficulty(value);
				case "estimatedMinutes", "recommendedMinMonths", "recommendedMaxMonths" ->
					value == null ? null : positiveInt(field, value);
				case "thumbnailUrl" -> value == null ? null : thumbnailUrl(value);
				case "recommendedBasis" -> value == null ? null : text(field, value, Integer.MAX_VALUE);
				case "castRoles" -> castRoles(value);
				case "sensitiveFlags" -> sensitiveFlags(value);
				default -> throw invalid(field);	// 모르는 필드 (죄명 · 법조문 · 요소 등은 이 API로 바꾸지 않는다)
			});
		}
		return new CaseEditRequest(changes);
	}

	boolean has(String field) {
		return changes.containsKey(field);
	}

	@SuppressWarnings("unchecked")
	<T> T get(String field) {
		return (T) changes.get(field);
	}

	private static String requiredText(String field, Object value, int maxLength) {
		String text = text(field, value, maxLength);
		if (text.isBlank()) {
			throw invalid(field);
		}
		return text;
	}

	private static String text(String field, Object value, int maxLength) {
		if (!(value instanceof String raw)) {
			throw invalid(field);
		}
		String text = raw.strip();
		if (text.length() > maxLength) {
			throw invalid(field);
		}
		return text;
	}

	private static String thumbnailUrl(Object value) {
		String url = text("thumbnailUrl", value, 300);
		if (!THUMBNAIL_URL.matcher(url).matches()) {
			throw invalid("thumbnailUrl");
		}
		return url;
	}

	private static List<String> keywords(Object value) {
		// 지울 때는 [] (jsonb 배열, ERD 7장). null · 문자열이 아닌 원소는 거절
		if (!(value instanceof List<?> list)) {
			throw invalid("keywords");
		}
		List<String> keywords = new ArrayList<>(list.size());
		for (Object item : list) {
			if (!(item instanceof String keyword) || keyword.isBlank()) {
				throw invalid("keywords");
			}
			keywords.add(keyword.strip());
		}
		return keywords;
	}

	/**
	 * 배역 (BE-125). null로 지울 수 없다 — 배정이 빠진 사건은 범죄 유형별 균형 계산에서 빠지고 화면도 기본 배치로
	 * 돌아간다. 바꾸려면 다른 배정을 보낸다
	 */
	private static Map<String, String> castRoles(Object value) {
		return CastRoles.parse(value).orElseThrow(() -> invalid("castRoles"));
	}

	/**
	 * 민감 플래그 (BE-125). 아는 플래그 이름만, 겹치면 하나로 줄인다. 모두 끌 때는 [] — null("분류하지 않음")로
	 * 되돌리지 않는다(keywords와 같은 관례)
	 */
	private static List<String> sensitiveFlags(Object value) {
		if (!(value instanceof List<?> list)) {
			throw invalid("sensitiveFlags");
		}
		Set<String> flags = new LinkedHashSet<>();
		for (Object item : list) {
			if (!(item instanceof String name) || SensitiveFlag.find(name).isEmpty()) {
				throw invalid("sensitiveFlags");
			}
			flags.add(name);
		}
		return List.copyOf(flags);
	}

	private static Difficulty difficulty(Object value) {
		try {
			return Difficulty.valueOf((String) value);
		} catch (ClassCastException | IllegalArgumentException e) {
			throw invalid("difficulty");
		}
	}

	private static Integer positiveInt(String field, Object value) {
		int number;
		if (value instanceof Integer integer) {
			number = integer;
		} else if (value instanceof String text && text.matches("\\d{1,9}")) {
			number = Integer.parseInt(text);
		} else {
			throw invalid(field);	// 소수 · 범위 밖 정수(Long · BigDecimal) · 그 밖의 타입
		}
		if (number < 1) {
			throw invalid(field);
		}
		return number;
	}

	private static BusinessException invalid(String field) {
		return BusinessException.ofField(ErrorCode.VALIDATION_ERROR, field);
	}
}
