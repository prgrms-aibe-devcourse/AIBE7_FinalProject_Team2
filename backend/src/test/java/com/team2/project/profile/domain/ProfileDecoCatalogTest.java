package com.team2.project.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 표 자체가 앞뒤가 맞는지 (BE-96). 표를 고칠 때 오타 · 없는 값을 잡는다 */
class ProfileDecoCatalogTest {

	private static Stream<Rewards> allRewards() {
		return Stream.concat(Stream.of(ProfileDecoCatalog.FREE), ProfileDecoCatalog.ACHIEVEMENTS.stream().map(Achievement::rewards));
	}

	private static List<String> all(Function<Rewards, List<String>> type) {
		return allRewards().flatMap(rewards -> type.apply(rewards).stream()).toList();
	}

	@Test
	@DisplayName("도전과제 id는 겹치지 않고, 목표는 1 이상이다")
	void achievementIds() {
		List<String> ids = ProfileDecoCatalog.ACHIEVEMENTS.stream().map(Achievement::id).toList();
		assertThat(ids).doesNotHaveDuplicates().hasSize(8);
		assertThat(ProfileDecoCatalog.ACHIEVEMENTS).allSatisfy(item -> assertThat(item.goal()).isPositive());
	}

	@Test
	@DisplayName("보상 · FREE의 값은 모두 값 목록에 있다")
	void rewardsUseKnownValues() {
		assertThat(all(Rewards::frames)).allSatisfy(id -> assertThat(ProfileDecoCatalog.FRAMES).contains(id));
		assertThat(all(Rewards::backgrounds)).allSatisfy(id -> assertThat(ProfileDecoCatalog.BACKGROUNDS).contains(id));
		assertThat(all(Rewards::sashStyles)).allSatisfy(id -> assertThat(ProfileDecoCatalog.SASH_STYLES).contains(id));
		assertThat(all(Rewards::sashIcons)).allSatisfy(id -> assertThat(ProfileDecoCatalog.SASH_ICONS).contains(id));
		assertThat(all(Rewards::sashColors)).allSatisfy(key -> {
			String[] parts = key.split(":");
			assertThat(ProfileDecoCatalog.SASH_COLORS.get(parts[0])).contains(parts[1]);
		});
		assertThat(all(Rewards::kinds)).allSatisfy(kind -> assertThat(kind).matches("[a-z0-9/-]+"));
	}

	@Test
	@DisplayName("V35 해금 백필 SQL의 목표값이 표와 같다 (BE-83) — 표만 바뀌고 마이그레이션이 옛 값인 채로 머지되는 것 방지")
	void unlockBackfillMatchesCatalog() throws IOException {
		String sql;
		try (InputStream in = getClass().getResourceAsStream("/db/migration/V35__create_collection_item.sql")) {
			assertThat(in).as("V35 마이그레이션").isNotNull();
			sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		// ('cases-3',     s.completed >= 3) 처럼 목표가 있는 줄, ('personality', s.has_type) 처럼 있음/없음인 줄
		Map<String, Integer> goals = new HashMap<>();
		Matcher withGoal = Pattern.compile("\\('([a-z0-9-]+)',\\s*s\\.\\w+\\s*>=\\s*(\\d+)\\)").matcher(sql);
		while (withGoal.find()) {
			goals.put(withGoal.group(1), Integer.parseInt(withGoal.group(2)));
		}
		Matcher flag = Pattern.compile("\\('([a-z0-9-]+)',\\s*s\\.has_type\\)").matcher(sql);
		while (flag.find()) {
			goals.put(flag.group(1), 1);
		}

		Map<String, Integer> catalog = ProfileDecoCatalog.ACHIEVEMENTS.stream()
			.filter(item -> item.stat() != AchievementStat.COLLECTED)	// 배포 시점 수집 수는 0이라 백필하지 않는다
			.collect(Collectors.toMap(Achievement::id, Achievement::goal));
		assertThat(goals).isEqualTo(catalog);
	}

	@Test
	@DisplayName("기본값은 값 목록 안에 있고 처음부터 열린 것으로 저장할 수 있다")
	void defaultsAreValid() {
		assertThatCode(() -> ProfileDecoValidator.validate(ProfileDecoCatalog.DEFAULTS, ProfileDecoCatalog.FREE))
			.doesNotThrowAnyException();
		assertThat(ProfileDecoCatalog.DEFAULT_PORTRAITS).allSatisfy(path ->
			assertThat(ProfileDecoCatalog.FREE.kinds()).contains(ProfileDecoCatalog.kindOf(path)));
	}
}
