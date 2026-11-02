package com.team2.project.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.member.domain.AuthProvider;
import com.team2.project.member.domain.TermsVersion;
import com.team2.project.member.service.MemberLoginService;
import com.team2.project.member.service.MemberLoginService.LoginResult;
import com.team2.project.support.ApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 카카오 로그인 성공 처리의 회원 찾기 · 만들기와 익명 기록 연결 규칙 (BE-62 · docs/decisions/BE-62.md)
 * 처음 온 계정은 동의해야 회원이 만들어진다(BE-145 · docs/decisions/BE-145.md)
 * 실제 PostgreSQL에서 확인한다. 테스트가 만든 회원 · 익명 사용자만 지운다
 */
class MemberLoginServiceTest extends ApiIntegrationTest {

	@Autowired
	private MemberLoginService memberLoginService;

	private final List<String> kakaoIds = new ArrayList<>();
	private final List<UUID> anonymousIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (UUID id : anonymousIds) {
			jdbcTemplate.update("DELETE FROM anonymous_user WHERE id = ?", id);
		}
		for (String kakaoId : kakaoIds) {
			jdbcTemplate.update("""
				DELETE FROM anonymous_user WHERE member_id IN
				    (SELECT id FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?)
				""", kakaoId);
			jdbcTemplate.update("DELETE FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", kakaoId);
		}
	}

	private String newKakaoId() {
		String id = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L));
		kakaoIds.add(id);
		return id;
	}

	private UUID insertAnonymous(Long memberId) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO anonymous_user (id, member_id) VALUES (?, ?)", id, memberId);
		anonymousIds.add(id);
		return id;
	}

	private Long memberOf(UUID anonymousId) {
		return jdbcTemplate.queryForObject("SELECT member_id FROM anonymous_user WHERE id = ?", Long.class, anonymousId);
	}

	private LoginResult login(String kakaoId, Optional<UUID> anonymousId) {
		LoginResult result = memberLoginService.signupWithTerms(AuthProvider.KAKAO, kakaoId, anonymousId);
		result.issuedAnonymousId().ifPresent(anonymousIds::add);
		return result;
	}

	@Test
	void 같은_카카오_계정은_같은_회원이다() {
		String kakaoId = newKakaoId();
		long first = login(kakaoId, Optional.empty()).memberId();
		long second = login(kakaoId, Optional.empty()).memberId();
		assertThat(second).isEqualTo(first);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", Integer.class, kakaoId))
			.isEqualTo(1);
	}

	@Test
	void 동의하고_가입한_카카오_회원은_동의_시각과_약관_버전이_함께_남는다() {
		long memberId = login(newKakaoId(), Optional.empty()).memberId();
		assertThat(jdbcTemplate.queryForMap("SELECT terms_agreed_at, terms_version FROM member WHERE id = ?", memberId))
			.doesNotContainEntry("terms_agreed_at", null)
			.containsEntry("terms_version", TermsVersion.CURRENT);
	}

	@Test
	void 처음_온_카카오_계정은_로그인만으로는_회원이_만들어지지_않는다() {
		String kakaoId = newKakaoId();

		Optional<LoginResult> result = memberLoginService.loginExisting(AuthProvider.KAKAO, kakaoId, Optional.empty());

		assertThat(result).isEmpty();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM member WHERE provider = 'KAKAO' AND provider_user_id = ?", Integer.class, kakaoId)).isZero();
	}

	@Test
	void 이미_가입한_카카오_회원은_loginExisting으로_같은_회원이_로그인한다() {
		String kakaoId = newKakaoId();
		long memberId = login(kakaoId, Optional.empty()).memberId();

		Optional<LoginResult> result = memberLoginService.loginExisting(AuthProvider.KAKAO, kakaoId, Optional.empty());
		result.flatMap(LoginResult::issuedAnonymousId).ifPresent(anonymousIds::add);

		assertThat(result).isPresent();
		assertThat(result.get().memberId()).isEqualTo(memberId);
	}

	@Test
	void 같은_계정으로_다시_동의해도_처음_동의_기록은_바뀌지_않는다() {
		String kakaoId = newKakaoId();
		long memberId = login(kakaoId, Optional.empty()).memberId();
		jdbcTemplate.update("UPDATE member SET terms_agreed_at = '2026-10-01 09:00:00+09', terms_version = 'old-version' WHERE id = ?",
			memberId);

		login(kakaoId, Optional.empty());

		assertThat(jdbcTemplate.queryForMap("SELECT terms_version FROM member WHERE id = ?", memberId))
			.containsEntry("terms_version", "old-version");
	}

	@Test
	void 연결_안_된_익명_기록은_이_회원에_연결하고_새로_발급하지_않는다() {
		UUID anonymousId = insertAnonymous(null);
		LoginResult result = login(newKakaoId(), Optional.of(anonymousId));
		assertThat(result.issuedAnonymousId()).isEmpty();
		assertThat(memberOf(anonymousId)).isEqualTo(result.memberId());
	}

	@Test
	void 이미_같은_회원에_연결된_익명_기록은_그대로다() {
		String kakaoId = newKakaoId();
		UUID anonymousId = insertAnonymous(null);
		long memberId = login(kakaoId, Optional.of(anonymousId)).memberId();
		LoginResult again = login(kakaoId, Optional.of(anonymousId));
		assertThat(again.issuedAnonymousId()).isEmpty();
		assertThat(memberOf(anonymousId)).isEqualTo(memberId);
	}

	@Test
	void 다른_회원의_익명_기록은_덮어쓰지_않고_새_익명_ID를_발급한다() {
		UUID anonymousId = insertAnonymous(null);
		long owner = login(newKakaoId(), Optional.of(anonymousId)).memberId();

		LoginResult other = login(newKakaoId(), Optional.of(anonymousId));

		assertThat(memberOf(anonymousId)).isEqualTo(owner);	// 앞 사람 기록은 그대로
		assertThat(other.issuedAnonymousId()).isPresent();
		assertThat(other.issuedAnonymousId().get()).isNotEqualTo(anonymousId);
		assertThat(memberOf(other.issuedAnonymousId().get())).isEqualTo(other.memberId());
	}

	@Test
	void 익명_쿠키가_없거나_DB에_없으면_회원에_연결된_새_익명_ID를_발급한다() {
		LoginResult noCookie = login(newKakaoId(), Optional.empty());
		assertThat(noCookie.issuedAnonymousId()).isPresent();
		assertThat(memberOf(noCookie.issuedAnonymousId().get())).isEqualTo(noCookie.memberId());

		LoginResult unknown = login(newKakaoId(), Optional.of(UUID.randomUUID()));
		assertThat(unknown.issuedAnonymousId()).isPresent();
		assertThat(memberOf(unknown.issuedAnonymousId().get())).isEqualTo(unknown.memberId());
	}

	@Test
	void 같은_회원이_다른_브라우저에서_로그인하면_그_익명_기록도_연결된다() {
		String kakaoId = newKakaoId();
		UUID browserA = insertAnonymous(null);
		UUID browserB = insertAnonymous(null);
		long memberId = login(kakaoId, Optional.of(browserA)).memberId();
		login(kakaoId, Optional.of(browserB));
		assertThat(memberOf(browserA)).isEqualTo(memberId);
		assertThat(memberOf(browserB)).isEqualTo(memberId);
	}

	@Test
	void 공급자_회원번호_형식이_다르면_회원을_만들지_않고_막는다() {
		for (String invalid : new String[] {"abc", "not-a-number", null}) {
			assertThatThrownBy(() -> memberLoginService.loginExisting(AuthProvider.KAKAO, invalid, Optional.empty()))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> memberLoginService.signupWithTerms(AuthProvider.KAKAO, invalid, Optional.empty()))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
