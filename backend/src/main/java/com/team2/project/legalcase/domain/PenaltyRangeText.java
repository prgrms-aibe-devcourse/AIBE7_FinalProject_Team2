package com.team2.project.legalcase.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** API 6 · 8에서 공통으로 사용하는 선고 가능 범위 표시 문구. */
public final class PenaltyRangeText {
	private PenaltyRangeText() { }

	public static String format(PenaltyType type, Long min, Long max) {
		if (min == null || max == null) {
			return switch (type) {
				case PRISON -> "징역";
				case FINE -> "벌금";
				case DEATH -> "사형";
				case LIFE -> "무기징역";
			};
		}
		if (type == PenaltyType.FINE) return "벌금 " + formatMoney(min) + " ~ " + formatMoney(max);
		String prison = "징역 " + formatMonths(min) + " ~ " + formatMonths(max);
		if (type == PenaltyType.PRISON) return prison;
		List<String> reductions = new ArrayList<>();
		if (type.canReduceTo(PenaltyType.LIFE)) reductions.add("무기징역");
		if (type.canReduceTo(PenaltyType.PRISON)) reductions.add(prison);
		return (type == PenaltyType.DEATH ? "사형" : "무기징역")
			+ (reductions.isEmpty() ? "" : " (감경하면 " + String.join(" 또는 ", reductions) + ")");
	}

	public static String formatMonths(long months) {
		if (months == 0) return "0개월";
		List<String> parts = new ArrayList<>();
		if (months / 12 > 0) parts.add(months / 12 + "년");
		if (months % 12 > 0) parts.add(months % 12 + "개월");
		return String.join(" ", parts);
	}

	public static String formatMoney(long amount) {
		List<String> parts = new ArrayList<>();
		long[] units = {100_000_000, 10_000, 1};
		String[] names = {"억", "만", ""};
		long rest = amount;
		for (int i = 0; i < units.length; i++) {
			long group = rest / units[i];
			if (group > 0) parts.add((group % 1000 == 0 ? group / 1000 + "천"
				: String.format(Locale.KOREA, "%,d", group)) + names[i]);
			rest %= units[i];
		}
		return (parts.isEmpty() ? "0" : String.join(" ", parts)) + " 원";
	}
}
