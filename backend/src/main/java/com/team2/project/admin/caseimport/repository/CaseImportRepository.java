package com.team2.project.admin.caseimport.repository;

import com.team2.project.admin.caseimport.domain.CaseImport;
import com.team2.project.admin.caseimport.domain.CaseImportStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
