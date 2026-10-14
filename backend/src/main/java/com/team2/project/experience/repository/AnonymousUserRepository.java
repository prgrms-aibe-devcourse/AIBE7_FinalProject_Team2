package com.team2.project.experience.repository;

import com.team2.project.experience.domain.AnonymousUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface AnonymousUserRepository extends JpaRepository<AnonymousUser, UUID> {

	/**
	 * 최근 접속 시각 갱신. 마지막 갱신이 threshold보다 오래됐을 때만 바꿔, 조회 요청마다 UPDATE가 나가지 않게 한다.
	 * 조회 API는 @Transactional(readOnly = true) 안에서 부르므로, 읽기 전용 트랜잭션에서도 쓸 수 있게 별도 트랜잭션으로 실행한다.
	 * @return 갱신한 행 수 (0이면 최근에 이미 갱신됨)
	 */
	@Modifying
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Query("update AnonymousUser a set a.lastSeenAt = :now where a.id = :id and a.lastSeenAt < :threshold")
	int touchIfStale(@Param("id") UUID id, @Param("now") Instant now, @Param("threshold") Instant threshold);

	/**
	 * 같은 사용자의 요청을 차례로 처리하기 위한 행 잠금 (SELECT ... FOR UPDATE). 트랜잭션이 끝날 때 풀린다.
	 * 성향 재응시(P-2)가 동시에 오면 둘 다 "현재 결과 내리기 → 저장"을 해서 부분 유니크 위반이 나므로 막는다 (#124 리뷰)
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AnonymousUser a where a.id = :id")
	Optional<AnonymousUser> findByIdForUpdate(@Param("id") UUID id);
}
