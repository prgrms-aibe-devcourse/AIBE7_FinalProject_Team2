package com.team2.project.admin.caseimport.repository;

import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportStatus;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseImportRepository extends JpaRepository<CaseImport, Long> {

	/** 진행 중인 등록 (A-14 IMPORT_IN_PROGRESS). 한 번에 하나만 돌린다 */
	List<CaseImport> findByStatusIn(Collection<CaseImportStatus> statuses);

	/**
	 * 이미 등록에 쓴 사건번호 (A-14 DUPLICATE_SOURCE).
	 *
	 * <p>적재가 들어가기 전(BE-93 2차 전)에는 `case_source`가 비어 있어 그 표만 봐서는 중복을 잡지 못한다.
	 * 그러면 같은 판결문으로 수십 분짜리 생성을 몇 번이고 다시 돌리게 된다 — 등록 요청 쪽도 함께 본다.
	 *
	 * <p>실패한 등록은 제외한다. 실패했으면 사건번호를 다시 쓸 수 있어야 한다.
	 */
	@Query("""
		select distinct s.caseNumber from CaseImportSource s
		where s.caseNumber in :caseNumbers and s.caseImport.status <> :excluded""")
	List<String> findCaseNumbersInUse(@Param("caseNumbers") Collection<String> caseNumbers,
		@Param("excluded") CaseImportStatus excluded);

	/**
	 * 적재할 때 행을 잠그고 읽는다 (BE-93 2차).
	 *
	 * <p>관리자가 버튼을 두 번 누르거나 네트워크가 재시도하면 적재 요청이 겹친다. 잠그지 않으면 둘 다
	 * "아직 적재 안 함"을 통과해 <b>같은 사건을 두 번 만들려다</b> 제목 유니크 제약에 걸린다 — 그때 나가는
	 * 응답은 "이미 적재했다"가 아니라 엉뚱한 코드다 (PR #242 리뷰).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from CaseImport i where i.id = :id")
	Optional<CaseImport> findByIdForUpdate(@Param("id") Long id);
}
