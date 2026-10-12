package com.team2.project.rag.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사건번호 해시 (rag_precedent.case_number_hashes). 자기 사건 제외 검색에만 쓴다.
 * 오프라인 도구 tools/ai-judgment/rag_store.py의 case_number_hash(es)와 같은 규칙이어야 한다:
 * 모든 공백(유니코드 공백 포함: NBSP U+00A0 · 전각 공백 U+3000 등, Python re의 \s와 같게)을 지운 사건번호의 UTF-8 SHA-256
 * hex(소문자 64자). 병합 표기("2014고13, 2014고14(병합)")는 번호마다 해시한다(ofAll).
 * 규칙을 바꾸면 두 곳을 함께 바꾸고 판례를 다시 적재한다.
 */
public final class CaseNumberHash {

	/** Java의 \s는 ASCII 공백만 잡으므로 유니코드 공백까지 지우게 (?U)를 켠다. Python과 다르면 자기 사건 제외가 조용히 실패한다 */
	private static final Pattern UNICODE_SPACE = Pattern.compile("(?U)\\s");

	/** 사건번호 하나 (예: 2014고13, 2008재고합1, 2016 고합 79). Python CASE_NUMBER_TOKEN과 같다 */
	private static final Pattern CASE_NUMBER_TOKEN = Pattern.compile("(?U)(?:19|20)\\d{2}\\s*[가-힣]{1,3}\\s*\\d+");

	private CaseNumberHash() {
	}

	/**
	 * 사건번호 칸 → 번호마다의 해시 (정렬 · 중복 제거). 형식에 맞는 번호가 없으면 칸 전체를 하나로 해시한다.
	 * 대상 사건 쪽 번호가 하나여도 판례 쪽 병합 번호 중 하나와 겹치면 자기 사건으로 빠진다.
	 */
	public static List<String> ofAll(String caseNumber) {
		if (caseNumber == null || caseNumber.isBlank()) {
			return List.of();
		}
		Set<String> tokens = new TreeSet<>();
		Matcher matcher = CASE_NUMBER_TOKEN.matcher(caseNumber);
		while (matcher.find()) {
			tokens.add(matcher.group());
		}
		if (tokens.isEmpty()) {
			tokens.add(caseNumber);
		}
		return tokens.stream().map(CaseNumberHash::of).distinct().sorted().toList();
	}

	/** 사건번호 하나의 해시 (공백 제거 후 SHA-256 hex) */
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
