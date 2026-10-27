package com.team2.project.member.repository;

import com.team2.project.member.domain.MemberPassword;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 아이디 회원 로그인 정보 (확장 단계, BE-113) */
public interface MemberPasswordRepository extends JpaRepository<MemberPassword, Long> {

	/** 로그인 아이디(소문자로 바꾼 값)로 찾는다 */
	Optional<MemberPassword> findByLoginId(String loginId);

	/** 가입 전 빠른 중복 확인. 동시 가입은 insertIfLoginIdAbsent가 최종으로 막는다 */
	boolean existsByLoginId(String loginId);

	/**
	 * 아이디가 아직 없을 때만 넣는다(0 또는 1 반환). 같은 아이디로 동시에 가입하면 한쪽만 1이 된다 —
	 * 유니크 위반 예외로 트랜잭션이 깨지지 않게 ON CONFLICT로 받는다 (BE-113 결정 4)
	 */
	@Modifying
	@Query(value = """
		INSERT INTO member_password (member_id, login_id, password_hash)
		VALUES (:memberId, :loginId, :passwordHash)
		ON CONFLICT (login_id) DO NOTHING
		""", nativeQuery = true)
	int insertIfLoginIdAbsent(@Param("memberId") long memberId, @Param("loginId") String loginId,
		@Param("passwordHash") String passwordHash);
}
