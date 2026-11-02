package com.team2.project.legalcase.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.judgment.domain.ReviewStatus;
import com.team2.project.legalcase.domain.CaseScript.RegeneratedBlock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 재생성 결과 반영 (BE-129 2차, {@link CaseScript#applyRegeneration}) — DB 없이 도메인 규칙만 본다 */
class CaseScriptRegenerationApplyTest {

	private static final Map<String, Object> CAST = Map.of("prosecutor", "naero", "defense", "nambul");

	@Test
	@DisplayName("증거조사만 있는 대본에 빠진 블록을 만들어 넣으면 pending에서 빠지고, 다섯 단위가 차면 승인할 수 있다")
	void fillsMissingUnits() {
		CaseScript script = CaseScript.of(null, "old", CAST, null, null, List.of("U0", "U1", "U2", "U4"), List.of(), null,
			true, null, true);
		script.addBlock(ScriptUnit.U3, List.of(Map.of("factorId", 1, "text", "대사")), 1, Map.of(), List.of(), List.of(),
			List.of());
		Map<ScriptUnit, RegeneratedBlock> made = new LinkedHashMap<>();
		made.put(ScriptUnit.U0, block(Map.of("questions", List.of(Map.of("a", "예"), Map.of("a", "예"), Map.of("a", "아니요"))), 1));
		made.put(ScriptUnit.U2, block(Map.of("questions", List.of()), 1));
		made.put(ScriptUnit.U4, block(Map.of("prosecutor", "의견"), 1));

		script.applyRegeneration(made, CAST, "denied", List.of(), Map.of(), false, "new");

		assertThat(script.getPending()).containsExactly("U1");
		assertThat(script.getAdmits()).isFalse();	// 새 U0 세 번째 답
		assertThat(script.getIssue()).isEqualTo("denied");
		assertThat(script.isStopped()).isFalse();
		assertThat(script.isApprovable()).isFalse();	// U1이 아직 없다

		script.applyRegeneration(Map.of(ScriptUnit.U1, block(Map.of("lines", List.of("해설")), 1)), CAST, "denied",
			List.of(), Map.of(), false, null);
		assertThat(script.getPending()).isEmpty();
		assertThat(script.isApprovable()).isTrue();
		assertThat(script.getModel()).isEqualTo("new");	// 모델이 오지 않으면 그대로 둔다
	}

	@Test
	@DisplayName("있는 블록은 바꾸고 승인을 지운다 — 판은 내려가지 않고, 검수 오류가 있으면 승인할 수 없는 블록이 된다")
	void replacesExistingBlock() {
		CaseScript script = CaseScript.of(null, "old", CAST, "admitted", true, List.of(), List.of(), null, true, null, false);
		for (ScriptUnit unit : ScriptUnit.values()) {
			script.addBlock(unit, unit == ScriptUnit.U3 ? List.of(Map.of("factorId", 1, "text", "대사")) : Map.of("v", 1),
				3, Map.of(), List.of(), List.of(), List.of());
		}
		script.approve(1L, Instant.now());

		script.applyRegeneration(Map.of(ScriptUnit.U4, new RegeneratedBlock(Map.of("prosecutor", "새"), 2, Map.of(),
			List.of(Map.of("severity", "error", "unit", "U4")), List.of(), List.of())), CAST, "admitted", List.of(), Map.of(),
			false, null);

		CaseScriptBlock u4 = script.block(ScriptUnit.U4).orElseThrow();
		assertThat(u4.getRevision()).isEqualTo(4);	// 결과 판(2)이 지금 판(3)보다 작으면 지금 판 + 1
		assertThat(u4.reviewState()).isEqualTo(ReviewStatus.PENDING);
		assertThat(u4.isPublishable()).isFalse();
		assertThat(script.block(ScriptUnit.U0).orElseThrow().reviewState()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(script.isPublishable()).isFalse();
	}

	@Test
	@DisplayName("결과에 메타(배역 · 쟁점)가 비어 오면 지금 값을 둔다")
	void keepsMetaWhenMissing() {
		CaseScript script = CaseScript.of(null, "old", CAST, "admitted", true, List.of(), List.of(), null, true, null, false);
		script.addBlock(ScriptUnit.U4, Map.of("v", 1), 1, Map.of(), List.of(), List.of(), List.of());

		script.applyRegeneration(Map.of(ScriptUnit.U4, block(Map.of("prosecutor", "새"), 2)), null, null, List.of(), Map.of(),
			false, null);

		assertThat(script.getCastRoles()).isEqualTo(CAST);
		assertThat(script.getIssue()).isEqualTo("admitted");
	}

	@Test
	@DisplayName("다시 만들지 않은 블록은 경고만 새 값으로 바꾼다 — 오류 · 승인 · 판은 그대로다 (BE-163)")
	void replacesWarningsOfUnchangedBlocks() {
		CaseScript script = CaseScript.of(null, "old", CAST, "admitted", true, List.of(), List.of(), null, true, null, false);
		Map<String, Object> oldWarning = Map.of("unit", "U2", "path", "/plea/0", "code", "FACT_AS_CLAIM", "severity", "warning");
		Map<String, Object> error = Map.of("unit", "U1", "path", "/lines/0", "code", "NUMBER_NOT_IN_SOURCE", "severity", "error");
		for (ScriptUnit unit : ScriptUnit.values()) {
			script.addBlock(unit, unit == ScriptUnit.U3 ? List.of(Map.of("factorId", 1, "text", "대사")) : Map.of("v", 1),
				3, Map.of(), unit == ScriptUnit.U2 ? List.of(oldWarning) : List.of(), List.of(), List.of());
		}
		script.approve(1L, Instant.now());
		script.block(ScriptUnit.U1).orElseThrow().getReview().add(error);	// 승인 뒤 생긴 오류 (적재 · 수정 경로를 흉내 낸다)

		Map<String, Object> cardWarning = Map.of("unit", "U2", "path", "", "code", "CARD_LIST_EMPTY", "severity", "warning");
		Map<String, Object> newError = Map.of("unit", "U2", "path", "", "code", "X", "severity", "error");	// 섞여 와도 버린다
		Map<String, Object> u1Warning = Map.of("unit", "U1", "path", "/lines/1", "code", "ONE_SIDED", "severity", "warning");
		Map<ScriptUnit, List<Map<String, Object>>> unchanged = new LinkedHashMap<>();
		unchanged.put(ScriptUnit.U2, List.of(cardWarning, newError));
		unchanged.put(ScriptUnit.U1, List.of(u1Warning));
		unchanged.put(ScriptUnit.U4, List.of(cardWarning));	// 다시 만든 단위는 결과 블록의 review를 쓴다

		script.applyRegeneration(Map.of(ScriptUnit.U4, block(Map.of("prosecutor", "새"), 4)), CAST, "admitted", List.of(),
			unchanged, false, null);

		CaseScriptBlock u2 = script.block(ScriptUnit.U2).orElseThrow();
		assertThat(u2.getReview()).containsExactly(cardWarning);	// 옛 경고는 지우고 새 경고만
		assertThat(u2.reviewState()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(u2.getRevision()).isEqualTo(3);
		assertThat(u2.isPublishable()).isTrue();
		CaseScriptBlock u1 = script.block(ScriptUnit.U1).orElseThrow();
		assertThat(u1.getReview()).containsExactly(error, u1Warning);	// 오류는 그대로 두고 경고만 더한다
		assertThat(u1.reviewState()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(script.block(ScriptUnit.U4).orElseThrow().getReview()).isEmpty();
		assertThat(script.block(ScriptUnit.U0).orElseThrow().getReview()).isEmpty();	// 결과에 없는 단위는 건드리지 않는다
	}

	private static RegeneratedBlock block(Map<String, Object> content, int revision) {
		return new RegeneratedBlock(content, revision, Map.of("case", 1), List.of(), List.of(), List.of());
	}
}
