package com.team2.project.admin.caseimport.load;

import tools.jackson.databind.JsonNode;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 파이프라인 결과 JSON에서 값을 꺼내는 도우미 (BE-93 2차).
 *
 * <p>없는 값을 조용히 넘기지 않는다. 파이썬 생성기는 필수 항목이 없으면 {@code KeyError}로 터져
 * "무엇이 없는지"가 스택트레이스에만 남았는데(포팅 메모 10-4), 여기서는 <b>어느 경로가 비었는지</b>를
 * 담은 검증 오류로 바꾼다. 관리자가 보는 화면에 그대로 쓸 수 있어야 하기 때문이다.
 */
final class Json {

	private Json() {
	}

	/** 있으면 문자열, 없거나 null이면 null */
	static String text(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	/** 반드시 있어야 하는 문자열 */
	static String requiredText(JsonNode node, String field, String path) {
		String value = text(node, field);
		if (value == null || value.isBlank()) {
			throw invalid(path);
		}
		return value;
	}

	static Integer integer(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value == null || value.isNull() ? null : value.asInt();
	}

	static Long longValue(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value == null || value.isNull() ? null : value.asLong();
	}

	static Long requiredLong(JsonNode node, String field, String path) {
		Long value = longValue(node, field);
		if (value == null) {
			throw invalid(path);
		}
		return value;
	}

	static boolean bool(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		return value != null && value.asBoolean();
	}

	/** 날짜. 형식이 틀리면 검증 오류 — 파이썬은 검사 없이 DB로 넘겨 거기서 터졌다(포팅 메모 10-5) */
	static LocalDate date(JsonNode node, String field, String path) {
		String value = text(node, field);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			throw invalid(path);
		}
	}

	/** 배열. 없으면 빈 목록 */
	static List<JsonNode> array(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		if (value == null || !value.isArray()) {
			return List.of();
		}
		List<JsonNode> items = new ArrayList<>();
		value.forEach(items::add);
		return items;
	}

	/** 문자열 배열. 없으면 빈 목록 (jsonb 컬럼에 []로 들어간다) */
	static List<String> stringArray(JsonNode node, String field) {
		return array(node, field).stream().filter(item -> !item.isNull()).map(JsonNode::asText).toList();
	}

	/** 객체를 Map으로. jsonb 컬럼에 그대로 넣을 때 쓴다 */
	static Map<String, Object> toMap(JsonNode node) {
		if (node == null || !node.isObject()) {
			return null;
		}
		Map<String, Object> map = new LinkedHashMap<>();
		node.properties().forEach(entry -> map.put(entry.getKey(), toPlain(entry.getValue())));
		return map;
	}

	/** 아무 값이나 jsonb에 넣을 자바 값(Map · List · 문자열 · 숫자 · 불리언)으로. null이면 null */
	static Object toPlain(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isObject()) {
			return toMap(node);
		}
		if (node.isArray()) {
			List<Object> items = new ArrayList<>();
			node.forEach(item -> items.add(toPlain(item)));
			return items;
		}
		if (node.isNumber()) {
			return node.isIntegralNumber() ? (Object) node.asLong() : (Object) node.asDouble();
		}
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		return node.asText();
	}

	/** enum으로 바꾼다. 모르는 값이면 어느 경로가 문제인지 알려 준다 */
	static <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
		if (value == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw invalid(path);
		}
	}

	static <E extends Enum<E>> E requiredEnum(Class<E> type, String value, String path) {
		E parsed = enumValue(type, value, path);
		if (parsed == null) {
			throw invalid(path);
		}
		return parsed;
	}

	static BusinessException invalid(String path) {
		return BusinessException.ofField(ErrorCode.IMPORT_RESULT_INVALID, path);
	}
}
