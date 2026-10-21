package com.team2.project.member.service;

import com.team2.project.experience.domain.AnonymousUser;
import com.team2.project.experience.repository.AnonymousUserRepository;
import com.team2.project.member.domain.AuthProvider;
import com.team2.project.member.domain.Member;
import com.team2.project.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 성공 처리: 회원 찾기 · 만들기 + 익명 기록 연결 (확장 단계, BE-62 · WBS 7.4.2, REQ-066 · 067)
 *
 * 익명 기록 연결 규칙 (docs/decisions/BE-62.md):
 * - 쿠키의 익명 ID가 아직 연결 안 됨 → 이 회원에 연결
 * - 이미 같은 회원에 연결됨 → 그대로
 * - 다른 회원에 연결됨(공용 PC) → 덮어쓰지 않고, 이 회원에 연결된 새 익명 ID를 발급한다.
 *   그대로 두면 이후 체험이 앞 사람 계정으로 쌓이기 때문이다
 * - 쿠키가 없거나 DB에 없는 값 → 이 회원에 연결된 새 익명 ID를 발급한다
 * 같은 회원이 다른 브라우저에서 로그인하면 그 브라우저의 익명 ID도 연결된다(회원 1 : 익명 N).
 * 기존 체험(experience.member_id)은 일괄로 채우지 않는다 — anonymous_user를 거쳐 찾는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberLoginService {

	/** 카카오 회원번호는 숫자다. 형식이 다르면 공급자 응답 이상으로 보고 로그인을 막는다 */
	private static final Pattern PROVIDER_USER_ID = Pattern.compile("[0-9]{1,64}");

	private final MemberRepository memberRepository;
	private final AnonymousUserRepository anonymousUserRepository;
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
		Optional<UUID> issued = linkAnonymous(member.getId(), anonymousId, now);
		return new LoginResult(member.getId(), issued);
	}

	private Optional<UUID> linkAnonymous(long memberId, Optional<UUID> anonymousId, Instant now) {
		if (anonymousId.isPresent()) {
			UUID id = anonymousId.get();
			if (anonymousUserRepository.linkIfUnlinked(id, memberId) == 1) {
				return Optional.empty();
			}
			Optional<Long> owner = anonymousUserRepository.findById(id).map(AnonymousUser::getMemberId);
			if (owner.isPresent() && owner.get() == memberId) {
				return Optional.empty();
			}
			if (owner.isPresent()) {
				log.info("다른 회원에 연결된 익명 기록이라 연결하지 않고 새 익명 ID를 발급한다");
			}
		}
		AnonymousUser fresh = anonymousUserRepository.save(AnonymousUser.issueFor(memberId, now));
		return Optional.of(fresh.getId());
	}

	/** 로그인 결과. issuedAnonymousId가 있으면 그 값을 익명 ID 쿠키로 바꿔 심는다 */
	public record LoginResult(long memberId, Optional<UUID> issuedAnonymousId) {
	}
}
