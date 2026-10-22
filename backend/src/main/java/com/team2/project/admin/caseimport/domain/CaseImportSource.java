package com.team2.project.admin.caseimport.domain;

import com.team2.project.legalcase.domain.CourtLevel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 등록 요청에 들어온 판결문 한 건 (BE-93). 적재할 때 case_source로 옮기므로 컬럼을 그 표와 맞춰 두었다.
 *
 * <p><b>원문을 여기에 두는 이유</b>: 파이프라인 서비스는 판결문 원문을 결과로 돌려주지 않는다
 * (요구사항 FR-5-3, COMMON-50). 그런데 적재 대상인 case_source.original_text에는 원문이 들어가야 한다.
 * 원문을 다시 받아오려고 서비스 쪽 경계를 무르게 만드는 대신, A-14에서 이미 받은 원문을 백엔드가 들고
 * 있다가 적재할 때 옮긴다. 적재가 끝나면 {@link #clearOriginalText()}로 비워 두 벌로 남기지 않는다.
 */
@Entity
@Table(name = "case_import_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseImportSource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "import_id")
	private CaseImport caseImport;

	@Enumerated(EnumType.STRING)
	private CourtLevel courtLevel;

	private String caseNumber;

	private String courtName;

	private LocalDate decidedAt;

	private boolean isFinal;

	/** 출처 기관. A-14에서 선택 값이라 비어 있을 수 있고, 적재할 때 기본값을 채운다 */
	private String sourceOrg;

	/** 판결문 원문. 적재가 끝나면 비운다 (위 머리말 참고) */
	private String originalText;

	private String note;

	/** 요청에 들어온 순서 (심급 순서와 다를 수 있다) */
	private int displayOrder;

	public static CaseImportSource of(CourtLevel courtLevel, String caseNumber, String courtName,
			LocalDate decidedAt, boolean isFinal, String sourceOrg, String originalText, String note) {
		CaseImportSource source = new CaseImportSource();
		source.courtLevel = courtLevel;
		source.caseNumber = caseNumber;
		source.courtName = courtName;
		source.decidedAt = decidedAt;
		source.isFinal = isFinal;
		source.sourceOrg = sourceOrg;
		source.originalText = originalText;
		source.note = note;
		return source;
	}

	/** CaseImport.addSource에서만 부른다 */
	void assignTo(CaseImport caseImport, int displayOrder) {
		this.caseImport = caseImport;
		this.displayOrder = displayOrder;
	}

	/** 적재해 case_source에 원문이 생긴 뒤 부른다 (2차 PR) */
	public void clearOriginalText() {
		this.originalText = null;
	}
}
