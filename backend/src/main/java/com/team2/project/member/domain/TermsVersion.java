package com.team2.project.member.domain;

/**
 * 회원가입 때 동의 기록(member.terms_version)에 남기는 약관 버전 (확장 단계, BE-133 · V44)
 *
 * 가입 화면의 동의 한 줄은 이용약관과 개인정보처리방침을 함께 묻는다 → 두 문서를 한 버전으로 묶는다.
 * 문서(frontend/src/app/legalPage.js)를 바꾸면 이 값도 함께 올린다. 이미 동의한 회원의 값은 바꾸지 않는다(그때 동의한 버전이다).
 * 지금 문서는 법무 검토 전 초안이고 시행일이 없어 날짜 대신 초안 표시를 쓴다. 시행일이 정해지면 그 날짜로 바꾼다 (docs/decisions/BE-133.md 결정 3)
 */
public final class TermsVersion {

	/** 지금 가입 화면에 보이는 약관 버전. DB 칸 길이(varchar 20)를 넘지 않는다 */
	public static final String CURRENT = "2026-10-draft";

	private TermsVersion() {
	}
}
