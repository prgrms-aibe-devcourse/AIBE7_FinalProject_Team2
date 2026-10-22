package com.team2.project.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.function.Function;
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
	@DisplayName("기본값은 값 목록 안에 있고 처음부터 열린 것으로 저장할 수 있다")
	void defaultsAreValid() {
		assertThatCode(() -> ProfileDecoValidator.validate(ProfileDecoCatalog.DEFAULTS, ProfileDecoCatalog.FREE))
			.doesNotThrowAnyException();
		assertThat(ProfileDecoCatalog.DEFAULT_PORTRAITS).allSatisfy(path ->
			assertThat(ProfileDecoCatalog.FREE.kinds()).contains(ProfileDecoCatalog.kindOf(path)));
	}
}
