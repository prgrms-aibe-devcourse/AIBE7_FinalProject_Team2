package com.team2.project.admin.review;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 신문 지면 수정 본문 (BE-92). CaseEditRequest(A-11)와 같은 방식 — 보내지 않은 필드는 그대로,
 * null을 보내면 지운다(headline · article은 필수라 null 불가).
 */
record CasePaperEditRequest(Map<String, Object> changes) {

	static final Set<String> FIELDS = Set.of("headline", "deck", "caption", "article");

	static CasePaperEditRequest parse(Map<String, Object> body) {
		if (body == null || body.isEmpty()) {
			throw invalid("body");
		}
		Map<String, Object> changes = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : body.entrySet()) {
			String field = entry.getKey();
			Object value = entry.getValue();
			changes.put(field, switch (field) {
				case "headline" -> requiredText(field, value, 200);
				case "deck" -> value == null ? null : text(field, value, 300);
				case "caption" -> value == null ? null : text(field, value, 200);
				case "article" -> article(value);
				default -> throw invalid(field);
			});
		}
		return new CasePaperEditRequest(changes);
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

	private static List<String> article(Object value) {
		if (!(value instanceof List<?> list) || list.isEmpty()) {
			throw invalid("article");
		}
		List<String> article = new ArrayList<>(list.size());
		for (Object item : list) {
			if (!(item instanceof String paragraph) || paragraph.isBlank()) {
				throw invalid("article");
			}
			article.add(paragraph.strip());
		}
		return article;
	}

	private static BusinessException invalid(String field) {
		return BusinessException.ofField(ErrorCode.VALIDATION_ERROR, field);
	}
}
