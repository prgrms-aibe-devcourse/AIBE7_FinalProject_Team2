package com.team2.project.community.service;

import com.team2.project.community.dto.AuthorCard;
import com.team2.project.community.dto.AuthorCard.CardLine;
import com.team2.project.level.dto.LevelStatus;
import com.team2.project.level.service.LevelService;
import com.team2.project.member.domain.Member;
import com.team2.project.member.repository.MemberRepository;
import com.team2.project.profile.domain.ProfileDeco;
import com.team2.project.profile.domain.ProfileDecoCatalog;
import com.team2.project.profile.domain.ProfileDecoValues;
import com.team2.project.profile.domain.ProfileSettingsValues;
import com.team2.project.profile.domain.ProfileStats;
import com.team2.project.profile.repository.ProfileDecoRepository;
import com.team2.project.profile.service.ProfileSettingsService;
import com.team2.project.profile.service.ProfileStatsReader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글쓴이 명함 조립 (BE-115). 이름은 회원, 나머지(레벨 · 직업 · 꾸미기 · 카드 아래 줄 · 성향 · 소개)는 쓴 요청의 익명 ID 기준이다.
 * 한 응답 안에서 같은 글쓴이는 한 번만 계산한다. 글쓴이마다 열 개 남짓 조회하므로 목록이 길어지거나 랭킹 후보가 많아지면
 * 미리 계산해 두는 쪽으로 바꾼다(docs/decisions/BE-115.md)
 */
@Service
@RequiredArgsConstructor
public class AuthorCardAssembler {

	/** 닉네임이 없는 회원(카카오 회원)의 표시 이름 앞말. 뒤에 회원 번호를 붙여 사람마다 다르고 바뀌지 않는다 */
	static final String AUTO_NAME_PREFIX = "방청객 ";

	private final MemberRepository memberRepository;
	private final ProfileSettingsService profileSettingsService;
	private final ProfileStatsReader statsReader;
	private final LevelService levelService;
	private final ProfileDecoRepository profileDecoRepository;
	private final ShownTypeResolver shownTypeResolver;

	/** 글 · 댓글을 쓴 사람: 회원 + 명함 기준 익명 ID */
	public record Writer(long memberId, UUID anonymousUserId) {
	}

	@Transactional(readOnly = true)
	public Map<Writer, AuthorCard> cards(Collection<Writer> writers) {
		Set<Writer> distinct = new LinkedHashSet<>(writers);
		if (distinct.isEmpty()) {
			return Map.of();
		}
		Set<Long> memberIds = distinct.stream().map(Writer::memberId).collect(Collectors.toSet());
		Map<Long, String> names = memberRepository.findAllById(memberIds).stream()
			.collect(Collectors.toMap(Member::getId, member -> displayName(member.getId(), member.getNickname())));
		return distinct.stream().collect(Collectors.toMap(Function.identity(),
			writer -> card(writer, names.getOrDefault(writer.memberId(), displayName(writer.memberId(), null))),
			(a, b) -> a, LinkedHashMap::new));
	}

	static String displayName(long memberId, String nickname) {
		return nickname == null || nickname.isBlank() ? AUTO_NAME_PREFIX + memberId : nickname;
	}

	private AuthorCard card(Writer writer, String name) {
		UUID id = writer.anonymousUserId();
		ProfileSettingsValues settings = profileSettingsService.view(id);
		ProfileStats stats = statsReader.read(id);
		LevelStatus level = levelService.view(id, stats);
		ProfileDecoValues deco = profileDecoRepository.findById(id).map(ProfileDeco::values).orElse(ProfileDecoCatalog.DEFAULTS);
		return new AuthorCard(name, settings.bio(), level.level(), level.job(),
			new CardLine(settings.cardLine(), settings.cardLevel()), stats.completed(), deco,
			shownTypeResolver.resolve(id, settings.shownCard()));
	}
}
