package com.team2.project.admin.repository;

import com.team2.project.admin.domain.AdminAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

	/** email은 AdminAccount.normalizeEmail을 거친 값으로 찾는다 */
	Optional<AdminAccount> findByEmail(String email);

	boolean existsByEmail(String email);
}
