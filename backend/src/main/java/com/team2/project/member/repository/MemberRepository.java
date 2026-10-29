package com.team2.project.member.repository;

import com.team2.project.member.domain.AuthProvider;
import com.team2.project.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

	/** 대소문자를 구분하지 않고 이미 쓰는 닉네임인가 (uq_member_nickname과 같은 기준) */
	boolean existsByNicknameIgnoreCase(String nickname);

	/** 나를 뺀 다른 회원이 쓰는 닉네임인가 (내 닉네임의 대소문자만 바꾸는 것은 허용) */
	boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);

	Optional<Member> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

	/**
	 * 없을 때만 회원을 만든다. 같은 계정의 첫 로그인이 동시에 와도 유니크(uq_member_provider_user) 위반 대신
	 * 한쪽만 들어간다(0 또는 1 반환). 만든 뒤 findByProviderAndProviderUserId로 읽는다
	 */
	@Modifying
	@Query(value = """
		INSERT INTO member (provider, provider_user_id)
		VALUES (:provider, :providerUserId)
		ON CONFLICT (provider, provider_user_id) DO NOTHING
		""", nativeQuery = true)
	int insertIfAbsent(@Param("provider") String provider, @Param("providerUserId") String providerUserId);
}
