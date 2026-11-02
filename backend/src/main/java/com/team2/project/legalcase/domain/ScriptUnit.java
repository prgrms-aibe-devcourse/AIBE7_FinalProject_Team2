package com.team2.project.legalcase.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 대본 단위 블록 (BE-126). 블록마다 "만들 때 본 값"({@link #basis()})이 정해져 있고, 그 값이 바뀌면 블록이 낡는다.
 *
 * <p><b>정본은 파이썬이다</b>(`tools/ai-judgment/script_assembler.py`의 {@code BLOCK_BASIS}). 여기는 같은 표를 옮긴 것이고,
 * 둘이 같은지는 공용 계약 파일(`tools/ai-judgment/contracts/script_blocks.json`)을 읽는 테스트가 확인한다
 * ({@code ScriptBlockContractTest}). 표를 바꾸면 파이썬 → 계약 파일 → 여기 순서로 고친다.
 */
public enum ScriptUnit {

	/** 설계도 (신문 질문 · 답, 쟁점). 화면에 직접 나가지 않고 U2 · U4가 보고 만든다 */
	U0(List.of("case", "mood", "issue", "victim", "thirdParty", "sensitiveFlags"), List.of()),
	/** 1막 해설 */
	U1(List.of("mood", "sensitiveFlags", "paper"), List.of("/lines/*")),
	/** 재판 도입부 (공소 · 입장 · 쟁점 · 신문) */
	U2(List.of("case", "cast", "mood", "issue", "thirdParty", "sensitiveFlags", "U0"),
		List.of("/indict/*", "/plea/*", "/issues_narr/*", "/questions/*/q")),
	/** 증거조사 대사 (판단 요소마다 한 줄). 대사 수정(A-13-13)이 늘 대사로 보므로 자리 목록을 두지 않는다 */
	U3(List.of("case", "cast", "victim", "thirdParty", "sensitiveFlags"), List.of()),
	/** 3막 검사 · 변호인 의견 */
	U4(List.of("case", "cast", "mood", "issue", "victim", "sensitiveFlags", "U0", "U2"), List.of("/prosecutor", "/defense"));

	/**
	 * 파이프라인이 모르는 값 — DB가 가진 사건 내용 · 기사. 파이프라인은 {@code null}로 내보내고 적재할 때 DB의 판
	 * ({@link LegalCase#getContentRevision()} · {@link CasePaper#getRevision()})으로 채운다
	 */
	public static final String CASE_BASIS = "case";
	public static final String PAPER_BASIS = "paper";
	public static final List<String> DB_BASIS = List.of(CASE_BASIS, PAPER_BASIS);

	private final List<String> basis;
	private final List<String> spokenPaths;
	private final List<Pattern> spokenPatterns;

	ScriptUnit(List<String> basis, List<String> spokenPaths) {
		this.basis = basis;
		this.spokenPaths = spokenPaths;
		// '*'는 배열 순번 하나(숫자 토큰)다. 나머지 글자는 그대로 비교한다
		this.spokenPatterns = spokenPaths.stream()
			.map(path -> Pattern.compile(Arrays.stream(path.split("\\*", -1)).map(Pattern::quote)
				.collect(Collectors.joining("\\d+"))))
			.toList();
	}

	/** 이 블록을 만들 때 본 값의 키. 앞 블록은 단위 이름(U0 · U2)이다 */
	public List<String> basis() {
		return basis;
	}

	/**
	 * 말하는 대사 자리 (BE-153) — 검수가 대사 길이(100자 · 2줄)를 거는 자리다(파이썬 {@code script_review.unit_lines}의
	 * guide · narrator · prosecutor · defense, 계약 파일 {@code spokenPaths}). 문장 수정(A-13-21)은 이 자리에만 길이 규칙을 건다 —
	 * 설계도(U0) · U2 카드 · 신문 답은 화면 대사가 아니다
	 */
	public List<String> spokenPaths() {
		return spokenPaths;
	}

	/** 그 자리(JSON Pointer)가 말하는 대사인지 */
	public boolean isSpoken(String path) {
		return path != null && spokenPatterns.stream().anyMatch(pattern -> pattern.matcher(path).matches());
	}

	/** 이 블록이 바뀌면 낡는 뒤 블록 (파이썬 {@code script_repair.AFFECTS}와 같다) */
	public List<ScriptUnit> affects() {
		return Arrays.stream(values()).filter(later -> later.basis.contains(name())).toList();
	}

	public static Optional<ScriptUnit> find(String name) {
		return Arrays.stream(values()).filter(unit -> unit.name().equals(name)).findFirst();
	}
}
