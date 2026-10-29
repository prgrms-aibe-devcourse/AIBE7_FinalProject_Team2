package com.team2.project.legalcase.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 사건 민감 플래그 (legal_case.sensitive_flags, BE-125). 파이프라인 분류(BE-123)가 켜고 관리자가 고친다.
 *
 * <p>값 목록의 기준은 파이프라인 {@code tools/ai-judgment/script_assembler.py}의 {@code SENSITIVE_FLAGS}다.
 * 그쪽에 플래그를 더하면 여기에도 더한다(두 목록이 같은지는 {@code SensitiveFlagContractTest}가 본다). 여기 없는 값도 적재할 때
 * 이름 그대로 저장되지만 공개 API로는 나가지 않고, 관리자 수정(A-11)에서는 거절된다.
 * DB에는 CHECK가 없다(V37 머리말).
 *
 * <p>{@code notice}는 사용자에게 안내하는 플래그인지다(사건 상세 알아두기 · 2막 시작 지문, FE-129). 나머지는
 * 대본 생성 가드 전용이라 공개 API로 내보내지 않는다 — 예를 들어 "사형 · 무기형 사건"을 사용자에게 미리
 * 알리면 판결 입력 전에 형량을 짐작하게 된다.
 */
public enum SensitiveFlag {
	VULNERABLE_VICTIM(true),	// 어린 · 고령 · 장애 피해자
	MENTAL_HEALTH(true),		// 정신건강
	SELF_HARM(true),			// 자해 · 자살
	PROFANITY_QUOTED(false),
	NATIONALITY_RELIGION(false),
	OVERSEAS(false),
	SEXUAL_OFFENSE(true),		// 성범죄
	DOMESTIC_VIOLENCE(true),	// 가정폭력 · 아동학대 · 스토킹
	JUVENILE_DEFENDANT(false),
	SUBSTANCE(false),
	HATE_CRIME(true),			// 혐오 · 차별
	CAPITAL_PENALTY(false);		// 법정형 문구로만 켜진다(모델에 묻지 않는다)

	private final boolean notice;

	SensitiveFlag(boolean notice) {
		this.notice = notice;
	}

	public boolean isNotice() {
		return notice;
	}

	/** 이름으로 찾는다. 모르는 값이면 비어 있다 */
	public static Optional<SensitiveFlag> find(String name) {
		return Arrays.stream(values()).filter(flag -> flag.name().equals(name)).findFirst();
	}

	/** 저장된 플래그 중 사용자에게 안내할 것만, 저장 순서대로 (공개 API 16 sensitiveFlags) */
	public static List<String> noticeOnly(List<String> flags) {
		List<String> notices = new ArrayList<>();
		for (String name : flags) {
			find(name).filter(SensitiveFlag::isNotice).ifPresent(flag -> notices.add(flag.name()));
		}
		return notices;
	}
}
