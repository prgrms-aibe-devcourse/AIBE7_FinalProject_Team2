package com.team2.project.member.service;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.common.exception.UniqueViolations;
import com.team2.project.member.domain.Member;
import com.team2.project.member.domain.MemberPassword;
import com.team2.project.member.domain.Nickname;
import com.team2.project.member.repository.MemberPasswordRepository;
import com.team2.project.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 아이디 · 비밀번호 회원가입(M-5) · 로그인(M-6) (확장 단계, BE-113 · docs/decisions/BE-113.md)
 *
 * - 비밀번호 해시 · 비교(Argon2, 일부러 느림) 동안 트랜잭션을 잡지 않는다(BE-67과 같은 이유). 회원 행 저장 ·
 *   마지막 로그인 시각 · 익명 기록 연결만 짧은 트랜잭션으로 묶는다
 * - 관리자 인증(AuthenticationManager)은 쓰지 않는다. PasswordEncoder를 직접 부르고, 토큰은 호출한 쪽이
 *   MemberPrincipal.user(id)로 발급한다 → 회원 로그인으로 관리자 권한이 생기지 않는다 (결정 3)
 * - 로그인 실패는 사유를 나누지 않는다(INVALID_CREDENTIALS). 없는 아이디도 더미 해시와 비교해 응답 시간을 맞춘다
 * - 로그에는 아이디 · 비밀번호 원문을 남기지 않는다
 */
@Slf4j
@Service
public class LocalAccountService {

	/** 저장 · 비교하는 아이디 형식(소문자로 바꾼 뒤). DB CHECK(ck_member_password_login_id)와 같다 */
	private static final Pattern LOGIN_ID = Pattern.compile("[a-z0-9_]{4,20}");

	private final MemberRepository memberRepository;
	private final MemberPasswordRepository passwordRepository;
	private final AnonymousLinkService anonymousLinkService;
	private final PasswordEncoder passwordEncoder;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	/** 없는 아이디로 로그인할 때 비교할 해시. 진짜 해시와 같은 인코더 · 설정이라 비교 시간이 같다 */
	private final String dummyHash;

	public LocalAccountService(MemberRepository memberRepository, MemberPasswordRepository passwordRepository,
		AnonymousLinkService anonymousLinkService, PasswordEncoder passwordEncoder,
		TransactionTemplate transactionTemplate, Clock clock) {
		this.memberRepository = memberRepository;
		this.passwordRepository = passwordRepository;
		this.anonymousLinkService = anonymousLinkService;
		this.passwordEncoder = passwordEncoder;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * 가입하고 바로 로그인한다. 형식 검사(길이 · 허용 글자) · 약관 동의는 요청 DTO가 먼저 한다.
	 * 가입한 시각을 약관 동의 시각으로 남긴다(BE-133)
	 *
	 * @param agreed 이용약관 · 개인정보처리방침 동의. false면 아무것도 저장하지 않는다
	 * @throws BusinessException DUPLICATE_ID(이미 있는 아이디, 동시 가입 포함) · DUPLICATE_NICKNAME(이미 쓰는 닉네임, BE-139) · VALIDATION_ERROR(닉네임 형식 · 약관 미동의)
	 */
	public LocalLoginResult signup(String id, String password, String nickname, boolean agreed,
		Optional<UUID> anonymousId) {
		// DTO가 이미 막지만, 동의 없이 동의 기록이 남는 일이 없게 서비스도 한 번 더 본다 (BE-133)
		if (!agreed) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "agreed");
		}
		String loginId = normalize(id);
		if (!LOGIN_ID.matcher(loginId).matches()) {
			throw BusinessException.ofField(ErrorCode.VALIDATION_ERROR, "id");
		}
		Nickname.validate(nickname);
		// 해시는 비싸서, 이미 있는 아이디면 해시 전에 끝낸다. 동시 가입은 아래 insertIfLoginIdAbsent가 막는다
		if (passwordRepository.existsByLoginId(loginId)) {
			throw new BusinessException(ErrorCode.DUPLICATE_ID);
		}
		if (memberRepository.existsByNicknameIgnoreCase(nickname)) {
			throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
		}
		String passwordHash = passwordEncoder.encode(password);	// 트랜잭션 밖

		return transactionTemplate.execute(status -> {
			Instant now = clock.instant();
			Member member = saveLocalMember(nickname, now);
			// 0이면 그사이 같은 아이디로 먼저 가입됐다 → 예외로 회원 행까지 되돌린다 (결정 4)
			if (passwordRepository.insertIfLoginIdAbsent(member.getId(), loginId, passwordHash) == 0) {
				throw new BusinessException(ErrorCode.DUPLICATE_ID);
			}
			member.recordLogin(now);
			Optional<UUID> issued = anonymousLinkService.link(member.getId(), anonymousId, now);
			log.info("아이디 회원가입: memberId={}", member.getId());
			return new LocalLoginResult(member.getId(), Nickname.displayName(member.getId(), member.getNickname()), issued);
		});
	}

	/**
	 * 아이디 · 비밀번호를 확인하고 로그인 처리(마지막 로그인 시각 · 익명 기록 연결)를 한다
	 *
	 * @throws BusinessException INVALID_CREDENTIALS(아이디 없음 · 형식 틀림 · 비밀번호 틀림을 구분하지 않는다)
	 */
	public LocalLoginResult login(String id, String password, Optional<UUID> anonymousId) {
		String loginId = normalize(id);
		Optional<MemberPassword> found = LOGIN_ID.matcher(loginId).matches()
			? passwordRepository.findByLoginId(loginId)
			: Optional.empty();
		// 없는 아이디도 더미 해시와 비교한다 — 응답 시간으로 아이디 존재 여부가 드러나지 않게 (결정 3)
		boolean matches = passwordEncoder.matches(password, found.map(MemberPassword::getPasswordHash).orElse(dummyHash));
		if (found.isEmpty() || !matches) {
			log.info("아이디 로그인 실패");	// 입력값 원문 · 실패 사유는 남기지 않는다
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		long memberId = found.get().getMemberId();

		return transactionTemplate.execute(status -> {
			Instant now = clock.instant();
			Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
			member.recordLogin(now);
			Optional<UUID> issued = anonymousLinkService.link(memberId, anonymousId, now);
			log.info("아이디 로그인: memberId={}", memberId);
			return new LocalLoginResult(memberId, Nickname.displayName(memberId, member.getNickname()), issued);
		});
	}

	/** 아이디는 소문자로 바꿔 저장 · 비교한다(Abc와 abc는 같은 아이디, 결정 2) */
	private static String normalize(String id) {
		return id == null ? "" : id.toLowerCase(Locale.ROOT);
	}

	/**
	 * 회원 행 저장. 같은 닉네임으로 동시에 가입하면 한쪽이 유니크(uq_member_nickname) 위반이 된다 → DUPLICATE_NICKNAME.
	 * 회원 행의 다른 유니크(공급자 + 회원번호)는 새 UUID라 겹칠 수 없어, 이 저장의 유니크 위반은 닉네임뿐이다
	 */
	private Member saveLocalMember(String nickname, Instant termsAgreedAt) {
		try {
			return memberRepository.save(Member.local(nickname, termsAgreedAt));
		} catch (DataIntegrityViolationException e) {
			if (UniqueViolations.isUniqueViolation(e)) {
				throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
			}
			throw e;
		}
	}

	/** 가입 · 로그인 결과. issuedAnonymousId가 있으면 그 값을 익명 ID 쿠키로 바꿔 심는다 */
	/** nickname은 화면에 보일 표시 이름이다: 닉네임이 있으면 그 값, 없으면(닉네임을 비운 회원) `방청객 {회원 ID}` — 항상 문자열 (PR #343 리뷰) */
	public record LocalLoginResult(long memberId, String nickname, Optional<UUID> issuedAnonymousId) {
	}
}
