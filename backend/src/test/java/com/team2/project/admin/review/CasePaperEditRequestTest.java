package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신문 지면 수정 본문 파싱 (BE-92, A-13-8). CaseEditRequest(A-11)와 같은 "보낸 필드만 · null = 지움" 규칙 */
class CasePaperEditRequestTest {

	@Test
	@DisplayName("headline에 null을 보내면 거절한다 (필수 필드)")
	void headlineNull_isRejected() {
		Map<String, Object> body = new HashMap<>();
		body.put("headline", null);

		assertThatThrownBy(() -> CasePaperEditRequest.parse(body)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
	}

	@Test
	@DisplayName("deck에 null을 보내면 지운다 (선택 필드)")
	void deckNull_isAllowed() {
		Map<String, Object> body = new HashMap<>();
		body.put("deck", null);

		CasePaperEditRequest edit = CasePaperEditRequest.parse(body);

		assertThat(edit.has("deck")).isTrue();
		assertThat((String) edit.get("deck")).isNull();
	}

	@Test
	@DisplayName("빈 article은 거절한다")
	void emptyArticle_isRejected() {
		Map<String, Object> body = Map.of("article", List.of());

		assertThatThrownBy(() -> CasePaperEditRequest.parse(body)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
	}

	@Test
	@DisplayName("보내지 않은 필드는 changes에 담기지 않는다")
	void unsentField_isNotInChanges() {
		Map<String, Object> body = Map.of("caption", "캡션");

		CasePaperEditRequest edit = CasePaperEditRequest.parse(body);

		assertThat(edit.has("headline")).isFalse();
		assertThat(edit.has("caption")).isTrue();
	}

	@Test
	@DisplayName("모르는 필드나 빈 본문은 거절한다")
	void unknownFieldOrEmptyBody_isRejected() {
		assertThatThrownBy(() -> CasePaperEditRequest.parse(Map.of())).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
		assertThatThrownBy(() -> CasePaperEditRequest.parse(Map.of("unknown", "값"))).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
	}
}
