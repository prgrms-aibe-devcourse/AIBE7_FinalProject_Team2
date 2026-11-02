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
 * 카카오 회원은 두 단계로 나뉜다 (BE-145, docs/decisions/BE-145.md):
 * - 이미 가입한 회원은 {@link #loginExisting}으로 바로 로그인한다.
 * - 처음 온 카카오 계정은 회원을 만들지 않는다 — 동의 화면에서 약관에 동의해야 {@link #signupWithTerms}로 회원이 만들어진다.
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
	 * 이미 가입한 회원이면 로그인 처리한다. 회원이 없으면 아무것도 만들지 않고 비어 있는 값을 돌려준다 —
	 * 부른 쪽이 동의 화면으로 보낸다(BE-145)
	 *
	 * @param anonymousId 요청의 익명 ID 쿠키(없으면 empty)
	 * @return 회원 ID, 그리고 새로 발급한 익명 ID(있으면 컨트롤러 · 핸들러가 쿠키로 내려준다)
	 */
	@Transactional
	public Optional<LoginResult> loginExisting(AuthProvider provider, String providerUserId, Optional<UUID> anonymousId) {
		requireValidProviderUserId(providerUserId);
		Optional<Member> found = memberRepository.findByProviderAndProviderUserId(provider, providerUserId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(complete(found.get(), anonymousId, clock.instant()));
	}

	/**
	 * 약관에 동의한 카카오 계정을 회원으로 만들고 로그인 처리한다 (M-12). 같은 계정이 이미 있으면(두 탭에서 동시에 동의 등)
	 * 새로 만들지 않고 그 회원으로 로그인하며, 동의 기록이 없을 때만 채운다 — 이미 있는 기록은 바꾸지 않는다.
	 * 회원 행과 동의 기록은 한 트랜잭션에서 함께 만든다
	 */
	@Transactional
	public LoginResult signupWithTerms(AuthProvider provider, String providerUserId, Optional<UUID> anonymousId) {
		requireValidProviderUserId(providerUserId);
		Instant now = clock.instant();
		memberRepository.insertIfAbsent(provider.name(), providerUserId);
		Member member = memberRepository.findByProviderAndProviderUserId(provider, providerUserId)
			.orElseThrow(() -> new IllegalStateException("회원 생성 직후 조회 실패"));
		member.agreeTerms(now);
		return complete(member, anonymousId, now);
	}

	private LoginResult complete(Member member, Optional<UUID> anonymousId, Instant now) {
		member.recordLogin(now);
		Optional<UUID> issued = anonymousLinkService.link(member.getId(), anonymousId, now);
		return new LoginResult(member.getId(), issued);
	}

	private static void requireValidProviderUserId(String providerUserId) {
		if (providerUserId == null || !PROVIDER_USER_ID.matcher(providerUserId).matches()) {
			throw new IllegalArgumentException("공급자 회원번호 형식이 올바르지 않다");
		}
	}

	/** 로그인 결과. issuedAnonymousId가 있으면 그 값을 익명 ID 쿠키로 바꿔 심는다 */
	public record LoginResult(long memberId, Optional<UUID> issuedAnonymousId) {
	}
}
