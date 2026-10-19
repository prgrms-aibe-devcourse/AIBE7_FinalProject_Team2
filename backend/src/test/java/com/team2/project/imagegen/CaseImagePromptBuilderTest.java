package com.team2.project.imagegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사건 사진 프롬프트 빌더 (BE-89, COMMON-41) — 얼굴 없는 회색 엑스트라, 내로 · 남불 미등장 규칙을 지키는지 확인한다
 */
class CaseImagePromptBuilderTest {

	private final CaseImagePromptBuilder builder = new CaseImagePromptBuilder();

	@Test
	@DisplayName("대표사진 프롬프트는 회색 엑스트라 · 마스코트 제외 문구를 포함한다")
	void buildRepresentativePhoto_includesStyleGuards() {
		String prompt = builder.buildRepresentativePhoto("a dispute over an unpaid loan between two neighbors");

		assertThat(prompt).contains("a dispute over an unpaid loan between two neighbors")
			.contains("featureless gray mannequin-like")
			.contains("no facial features")
			.contains("Do not include any cat or flame mascot characters");
	}

	@Test
	@DisplayName("발단 사진 프롬프트도 같은 스타일 규칙을 포함한다")
	void buildIncidentPhoto_includesStyleGuards() {
		String prompt = builder.buildIncidentPhoto("the moment the argument turned violent");

		assertThat(prompt).contains("the moment the argument turned violent")
			.contains("featureless gray mannequin-like")
			.contains("Do not include any cat or flame mascot characters");
	}

	@Test
	@DisplayName("빈 입력은 거절한다")
	void blankInput_isRejected() {
		assertThatThrownBy(() -> builder.buildRepresentativePhoto(" ")).isInstanceOf(ImageGenException.class);
		assertThatThrownBy(() -> builder.buildIncidentPhoto("")).isInstanceOf(ImageGenException.class);
	}
}
