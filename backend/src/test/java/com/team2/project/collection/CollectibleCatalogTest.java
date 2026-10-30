package com.team2.project.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.collection.dto.CollectionRecordRequest;
import com.team2.project.collection.service.CollectibleCatalog;
import com.team2.project.illustration.service.IllustrationCatalog;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 의상 화이트리스트가 일러스트 목록(BE-142)의 역할 의상 그림에서 나오는지 (BE-136). DB 없이 확인한다
 */
class CollectibleCatalogTest {

	@Test
	void 역할_의상_그림_이름이_의상_id가_된다() {
		assertThat(CollectibleCatalog.castIdsOf(List.of(
			"characters/roles/naero_judge.webp",
			"characters/roles/nambul_case-1.webp",
			"characters/roles/broken.webp",			// 밑줄 없음 — 인물 · 역할을 나눌 수 없다
			"characters/roles/sub/naero_x.webp",	// 하위 폴더는 보지 않는다
			"characters/scene/naero_judge.webp",	// 역할 의상 폴더가 아니다
			"cases/case-1-origin.webp")))
			.containsExactlyInAnyOrder("naero:judge", "nambul:case-1");
	}

	@Test
	void 지금_목록의_의상은_화면이_만드는_인물_역할을_모두_담고_형식_검사를_통과한다() {
		Set<String> castIds = CollectibleCatalog.castIdsOf(IllustrationCatalog.read());
		// 화면 castOf의 인물(naero · nambul) × 역할(actor · prosecutor · defense · judge) — frontend/src/utils/myPageView.js
		for (String who : List.of("naero", "nambul")) {
			for (String role : List.of("actor", "prosecutor", "defense", "judge")) {
				assertThat(castIds).contains(who + ":" + role);
			}
		}
		assertThat(castIds).allMatch(id -> id.matches(CollectionRecordRequest.ITEM_ID_PATTERN));
	}
}
