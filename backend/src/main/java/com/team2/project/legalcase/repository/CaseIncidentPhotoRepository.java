package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import com.team2.project.legalcase.domain.IncidentPhotoRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseIncidentPhotoRepository extends JpaRepository<CaseIncidentPhoto, Long> {

	/** 관리자 목록(A-13-5). role이 없는 행(1단계 이전에 3번째 이상 올라간 사진)도 그대로 보여 정리 대상을 드러낸다 */
	List<CaseIncidentPhoto> findByLegalCaseIdOrderByDisplayOrder(Long caseId);

	/** 역할별 1장 조회 · 교체 대상 찾기 (uk_case_incident_photo_case_role) */
	Optional<CaseIncidentPhoto> findByLegalCaseIdAndRole(Long caseId, IncidentPhotoRole role);
}
