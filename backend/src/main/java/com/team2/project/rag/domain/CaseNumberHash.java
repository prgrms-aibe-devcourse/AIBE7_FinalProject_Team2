package com.team2.project.rag.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 사건번호 해시 (rag_precedent.case_number_hash). 자기 사건 제외 검색에만 쓴다.
 * 오프라인 도구 tools/ai-judgment/rag_store.py의 case_number_hash와 같은 규칙이어야 한다:
 * 모든 공백(유니코드 공백 포함: NBSP U+00A0 · 전각 공백 U+3000 등, Python re의 \s와 같게)을 지운 사건번호의 UTF-8 SHA-256
 * hex(소문자 64자). 규칙을 바꾸면 두 곳을 함께 바꾸고 판례를 다시 적재한다.
 */
public final class CaseNumberHash {

	/** Java의 \s는 ASCII 공백만 잡으므로 유니코드 공백까지 지우게 (?U)를 켠다. Python과 다르면 자기 사건 제외가 조용히 실패한다 */
	private static final Pattern UNICODE_SPACE = Pattern.compile("(?U)\\s");

	private CaseNumberHash() {
	}

	public static String of(String caseNumber) {
		if (caseNumber == null || caseNumber.isBlank()) {
			throw new IllegalArgumentException("사건번호가 비어 있습니다");
		}
		String normalized = UNICODE_SPACE.matcher(caseNumber).replaceAll("");
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}
