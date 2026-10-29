package com.team2.project.legalcase;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.legalcase.domain.CasePhotoUrls;
import com.team2.project.legalcase.domain.IncidentPhotoRole;
import org.junit.jupiter.api.Test;

/** 사건 사진 주소 규칙 기본값 (BE-141): 저장된 값이 우선, 없거나 비어 있으면 case-{id} 규칙 주소 */
class CasePhotoUrlsTest {

	@Test
	void thumbnail_storedValueWins() {
		assertThat(CasePhotoUrls.thumbnail(7L, "https://cdn.example.test/a.jpg")).isEqualTo("https://cdn.example.test/a.jpg");
		assertThat(CasePhotoUrls.thumbnail(1L, "/images/cases/case-1-reenact.webp")).isEqualTo("/images/cases/case-1-reenact.webp");
	}

	@Test
	void thumbnail_nullOrBlank_usesRuleAddress() {
		assertThat(CasePhotoUrls.thumbnail(7L, null)).isEqualTo("/images/cases/case-7-reenact.webp");
		assertThat(CasePhotoUrls.thumbnail(7L, "  ")).isEqualTo("/images/cases/case-7-reenact.webp");
	}

	@Test
	void incident_ruleAddressPerRole() {
		assertThat(CasePhotoUrls.incident(7L, IncidentPhotoRole.ORIGIN)).isEqualTo("/images/cases/case-7-origin.webp");
		assertThat(CasePhotoUrls.incident(7L, IncidentPhotoRole.CONCLUSION)).isEqualTo("/images/cases/case-7-conclusion.webp");
	}
}
