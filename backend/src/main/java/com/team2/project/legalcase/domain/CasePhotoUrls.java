package com.team2.project.legalcase.domain;

/**
 * 사건 사진 주소의 규칙 기본값 (BE-141).
 *
 * <p>사진은 팀이 따로 제작해 프론트 정적 파일(frontend/public/images/cases/)로 두므로, 주소는 사건 번호만으로
 * 정해진다: {@code /images/cases/case-{id}-{reenact|origin|conclusion}.webp}. 사건 1이 이미 이 이름을 쓴다.
 * DB에 저장된 값(관리자 업로드, A-13-3 ~ 5)이 있으면 <b>그것이 우선</b>이고, 없을 때만 규칙 주소를 쓴다.
 * 규칙 주소의 파일이 없으면 404가 나고 화면이 공용 컷으로 바꾼다(FE-63). 사건 번호는 환경마다 같아야 한다.
 */
public final class CasePhotoUrls {

	private static final String PREFIX = "/images/cases/case-";

	private CasePhotoUrls() {
	}

	/** 대표 사진. 저장된 값이 비어 있으면 규칙 주소 */
	public static String thumbnail(Long caseId, String stored) {
		return isBlank(stored) ? rule(caseId, "reenact") : stored;
	}

	/** 발단 · 결론 사진 규칙 주소. 저장된 사진 행이 없을 때 쓴다 */
	public static String incident(Long caseId, IncidentPhotoRole role) {
		return rule(caseId, role == IncidentPhotoRole.ORIGIN ? "origin" : "conclusion");
	}

	private static String rule(Long caseId, String slot) {
		return PREFIX + caseId + "-" + slot + ".webp";
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
