package com.team2.project.legalcase.repository;

import com.team2.project.legalcase.domain.CaseIncidentPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseIncidentPhotoRepository extends JpaRepository<CaseIncidentPhoto, Long> {

	List<CaseIncidentPhoto> findByLegalCaseIdOrderByDisplayOrder(Long caseId);

	/** 다음 발단 사진의 순번(0부터). 사건마다 (case_id, display_order)가 유일해야 한다(uk_case_incident_photo_case_display_order) */
	int countByLegalCaseId(Long caseId);
}
