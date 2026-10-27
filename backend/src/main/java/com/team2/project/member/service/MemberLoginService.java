package com.team2.project.member.service;

import com.team2.project.member.domain.AuthProvider;
import com.team2.project.member.domain.Member;
import com.team2.project.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 성공 처리: 회원 찾기 · 만들기 + 익명 기록 연결 (확장 단계, BE-62 · WBS 7.4.2, REQ-066 · 067)
 *
 * 익명 기록 연결 규칙은 {@link AnonymousLinkService}(BE-62 결정 6)를 따른다. 아이디 로그인(BE-113)도 같은 규칙을 쓴다.
 * 기존 체험(experience.member_id)은 일괄로 채우지 않는다 — anonymous_user를 거쳐 찾는다
 */
@Service
@RequiredArgsConstructor
public class MemberLoginService {

	/** 카카오 회원번호는 숫자다. 형식이 다르면 공급자 응답 이상으로 보고 로그인을 막는다 */
	private static final Pattern PROVIDER_USER_ID = Pattern.compile("[0-9]{1,64}");

	private final MemberRepository memberRepository;
	private final AnonymousLinkService anonymousLinkService;
	private final Clock clock;

	/**
	 * @param anonymousId 요청의 익명 ID 쿠키(없으면 empty)
	 * @return 회원 ID, 그리고 새로 발급한 익명 ID(있으면 컨트롤러 · 핸들러가 쿠키로 내려준다)
	 */
	@Transactional
	public LoginResult login(AuthProvider provider, String providerUserId, Optional<UUID> anonymousId) {
		if (providerUserId == null || !PROVIDER_USER_ID.matcher(providerUserId).matches()) {
			throw new IllegalArgumentException("공급자 회원번호 형식이 올바르지 않다");
		}
		Instant now = clock.instant();
		memberRepository.insertIfAbsent(provider.name(), providerUserId);
		Member member = memberRepository.findByProviderAndProviderUserId(provider, providerUserId)
			.orElseThrow(() -> new IllegalStateException("회원 생성 직후 조회 실패"));
		member.recordLogin(now);
		Optional<UUID> issued = anonymousLinkService.link(member.getId(), anonymousId, now);
		return new LoginResult(member.getId(), issued);
	}

	/** 로그인 결과. issuedAnonymousId가 있으면 그 값을 익명 ID 쿠키로 바꿔 심는다 */
	public record LoginResult(long memberId, Optional<UUID> issuedAnonymousId) {
	}
}
