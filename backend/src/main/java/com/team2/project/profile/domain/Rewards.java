package com.team2.project.profile.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 보상(또는 열린 항목) 여섯 종류 (BE-96, FE-68 REWARD_TYPES와 같은 이름 · 순서).
 * sashColors는 "모양:색"(예: short:wine)으로 모양마다 따로 센다
 */
public record Rewards(
	List<String> kinds,
	List<String> frames,
	List<String> backgrounds,
	List<String> sashStyles,
	List<String> sashColors,
	List<String> sashIcons) {

	public static final Rewards NONE = new Rewards(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

	public Rewards {
		kinds = List.copyOf(kinds);
		frames = List.copyOf(frames);
		backgrounds = List.copyOf(backgrounds);
		sashStyles = List.copyOf(sashStyles);
		sashColors = List.copyOf(sashColors);
		sashIcons = List.copyOf(sashIcons);
	}

	/** 두 보상을 합친다. 순서는 이 보상 → other, 중복은 한 번만 */
	public Rewards plus(Rewards other) {
		return new Rewards(union(kinds, other.kinds), union(frames, other.frames), union(backgrounds, other.backgrounds),
			union(sashStyles, other.sashStyles), union(sashColors, other.sashColors), union(sashIcons, other.sashIcons));
	}

	private static List<String> union(List<String> a, List<String> b) {
		Set<String> merged = new LinkedHashSet<>(a);
		merged.addAll(b);
		return List.copyOf(merged);
	}

	/** 보상 목록 작성용 빌더 (받는 것만 적고 나머지는 빈 목록) */
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private List<String> kinds = List.of();
		private List<String> frames = List.of();
		private List<String> backgrounds = List.of();
		private List<String> sashStyles = List.of();
		private List<String> sashColors = List.of();
		private List<String> sashIcons = List.of();

		public Builder kinds(String... values) { kinds = List.of(values); return this; }
		public Builder frames(String... values) { frames = List.of(values); return this; }
		public Builder backgrounds(String... values) { backgrounds = List.of(values); return this; }
		public Builder sashStyles(String... values) { sashStyles = List.of(values); return this; }
		public Builder sashColors(String... values) { sashColors = List.of(values); return this; }
		public Builder sashIcons(String... values) { sashIcons = List.of(values); return this; }

		public Rewards build() {
			return new Rewards(kinds, frames, backgrounds, sashStyles, sashColors, sashIcons);
		}
	}
}
