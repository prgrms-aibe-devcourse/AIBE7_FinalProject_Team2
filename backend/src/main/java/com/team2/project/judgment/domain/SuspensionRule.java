package com.team2.project.judgment.domain;

/** 집행유예 법정 조건 (형법 제62조). */
public final class SuspensionRule {
	public static final int MAX_PRISON_MONTHS = 36;
	public static final long MAX_FINE_AMOUNT = 5_000_000L;
	public static final int MIN_MONTHS = 12;
	public static final int MAX_MONTHS = 60;

	private SuspensionRule() { }
}
