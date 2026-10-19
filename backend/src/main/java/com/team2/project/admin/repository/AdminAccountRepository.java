package com.team2.project.admin.repository;

import com.team2.project.admin.domain.AdminAccount;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

	/** email은 AdminAccount.normalizeEmail을 거친 값으로 찾는다 */
	Optional<AdminAccount> findByEmail(String email);

	boolean existsByEmail(String email);

	/** 세션 중 계정 확인 (AdminAccountStatusFilter, BE-67) */
	boolean existsByIdAndEnabledTrue(Long id);

	/**
	 * 마지막 로그인 시각만 짧은 트랜잭션으로 갱신한다 (BE-67). 로그인 전체를 트랜잭션으로 묶으면
	 * 비밀번호 확인(bcrypt, 일부러 느림) 동안 DB 연결을 붙잡고 있게 되어 따로 뺐다
	 */
	@Transactional
	@Modifying(clearAutomatically = true)
	@Query("UPDATE AdminAccount a SET a.lastLoginAt = :at WHERE a.id = :id")
	int updateLastLoginAt(@Param("id") Long id, @Param("at") Instant at);
}
