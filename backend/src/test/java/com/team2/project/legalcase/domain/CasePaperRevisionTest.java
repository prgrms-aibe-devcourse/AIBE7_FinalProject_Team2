package com.team2.project.legalcase.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기사의 판 (BE-126). U1 블록의 basis가 이 값을 남긴다 — 내용이 실제로 바뀔 때만 올라야 낡음이 잘못 뜨지 않는다 */
class CasePaperRevisionTest {

	@Test
	@DisplayName("새 기사는 1판, 같은 값으로 고치면 그대로, 다른 값이면 오르고, 다시 생성하면 오른다")
	void revision_bumpsOnlyOnChange() {
		CasePaper paper = CasePaper.of(null, "사회부", "헤드라인", null, "캡션", List.of("문단1", "문단2"));
		assertThat(paper.getRevision()).isEqualTo(1);

		paper.changeHeadline("헤드라인");
		paper.changeDeck(null);
		paper.changeArticle(List.of("문단1", "문단2"));
		assertThat(paper.getRevision()).isEqualTo(1);

		paper.changeCaption("다른 캡션");
		assertThat(paper.getRevision()).isEqualTo(2);

		paper.overwrite("사회부", "새 헤드라인", null, null, List.of("새 문단"));
		assertThat(paper.getRevision()).isEqualTo(3);
	}
}
