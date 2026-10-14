package com.team2.project.admin.review;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.legalcase.domain.Difficulty;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A-11 사건 내용 수정 본문 (API 명세 A-11 표). "보내지 않은 필드"와 "null을 보낸 필드"를 나눠야 해서
 * 본문을 Map으로 받아 필드마다 직접 검사한다(보내지 않음 = 그대로, null = 지움, 필수 필드는 null 불가).
 * 숫자는 정수만(소수 거절), 문자열 숫자("15")는 허용한다(API 명세 1-1).
 */
record CaseEditRequest(Map<String, Object> changes) {

	static final Set<String> FIELDS = Set.of("title", "shortIntro", "overview", "keywords", "difficulty",
		"estimatedMinutes", "thumbnailUrl", "recommendedMinMonths", "recommendedMaxMonths", "recommendedBasis");

	/** 본문을 검사해 바꿀 값만 담는다. 값은 검사를 마친 타입(String · List · Difficulty · Integer · null) */
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
				case "thumbnailUrl" -> value == null ? null : text(field, value, 300);
				case "recommendedBasis" -> value == null ? null : text(field, value, Integer.MAX_VALUE);
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
		if (!(value instanceof String text) || text.length() > maxLength) {
			throw invalid(field);
		}
		return text;
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
			keywords.add(keyword);
		}
		return keywords;
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
		} else if (value instanceof Long || value instanceof BigDecimal || value instanceof Double) {
			throw invalid(field);	// 범위 밖 정수 · 소수
		} else {
			throw invalid(field);
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
