package com.team2.project.collection.service;

import com.team2.project.collection.domain.CollectionKind;
import com.team2.project.collection.dto.CollectionRecordRequest.Item;
import com.team2.project.common.exception.FieldErrorDetail;
import com.team2.project.illustration.service.IllustrationCatalog;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 모을 수 있는 항목 목록 — 수집 기록(P-9) 화이트리스트 (BE-136, docs/decisions/BE-136.md)
 *
 * <ul>
 *   <li>일러스트: {@code case-{사건 id}-{origin|conclusion|reenact}} — 사건당 3장(FE-132 콘텐츠 규칙) · 사건 사진 주소 규칙(BE-141)과 같은 이름.
 *       그 사건이 <b>공개 중</b>이어야 한다. 요청한 사건과 같지 않아도 된다 — 같은 그림을 여러 사건이 다시 쓰는 그림별 key(BE-83 결정 3)라서.
 *       남는 여유(다른 사건 그림)는 사건당 상한({@code COLLECTION_ILLUSTRATIONS_PER_CASE})이 묶는다</li>
 *   <li>의상: 일러스트 목록(IllustrationCatalog, BE-142)의 {@code characters/roles/{who}_{role}.webp} → {@code {who}:{role}}.
 *       화면이 만난 모습을 그릴 그림이 있는 것만 모을 수 있다. 목록은 이미지 폴더와 어긋나면 IllustrationCatalogTest가 실패한다</li>
 * </ul>
 */
@Component
public class CollectibleCatalog {

	/** 사건 일러스트 id. 사건 id는 long에 들어가는 18자리까지(형식 검사가 64자로 먼저 막는다) */
	static final Pattern CASE_ILLUSTRATION = Pattern.compile("case-([1-9][0-9]{0,17})-(origin|conclusion|reenact)");

	/** 목록에 없는 항목의 details 사유 */
	public static final String NOT_COLLECTIBLE = "NOT_COLLECTIBLE";

	private static final String ROLES_FOLDER = "characters/roles/";

	private final LegalCaseRepository legalCaseRepository;
	private final Set<String> castIds;

	public CollectibleCatalog(LegalCaseRepository legalCaseRepository) {
		this.legalCaseRepository = legalCaseRepository;
		this.castIds = castIdsOf(IllustrationCatalog.read());
	}

	/** 일러스트 목록의 역할 의상 그림 → 의상 id. {@code characters/roles/naero_judge.webp} → {@code naero:judge} */
	public static Set<String> castIdsOf(List<String> imagePaths) {
		Set<String> ids = new TreeSet<>();
		for (String path : imagePaths) {
			if (!path.startsWith(ROLES_FOLDER)) {
				continue;
			}
			String name = path.substring(ROLES_FOLDER.length());
			int dot = name.lastIndexOf('.');
			String stem = dot < 0 ? name : name.substring(0, dot);
			int underscore = stem.indexOf('_');
			if (underscore > 0 && underscore < stem.length() - 1 && !stem.contains("/")) {
				ids.add(stem.substring(0, underscore) + ":" + stem.substring(underscore + 1));
			}
		}
		return Set.copyOf(ids);
	}

	/** 모을 수 있는 의상 id 전체 */
	public Set<String> castIds() {
		return castIds;
	}

	/**
	 * 목록에 없는 항목을 요청 순서대로 돌려준다. 비었으면 모두 모을 수 있다.
	 * field는 {@code items[i].itemId}(형식 검사의 칸 이름과 같은 모양), reason은 {@link #NOT_COLLECTIBLE}.
	 * 공개 여부는 일러스트가 가리키는 사건들을 한 번에 조회한다
	 */
	public List<FieldErrorDetail> rejected(List<Item> items) {
		Set<Integer> rejectedIndexes = new TreeSet<>();
		Map<Integer, Long> illustrationCases = new LinkedHashMap<>();
		for (int index = 0; index < items.size(); index++) {
			Item item = items.get(index);
			CollectionKind kind = CollectionKind.fromKey(item.kind()).orElse(null);
			if (kind == CollectionKind.ILLUSTRATION) {
				Matcher matcher = CASE_ILLUSTRATION.matcher(item.itemId());
				if (matcher.matches()) {
					illustrationCases.put(index, Long.parseLong(matcher.group(1)));
				} else {
					rejectedIndexes.add(index);
				}
			} else if (kind != CollectionKind.CAST || !castIds.contains(item.itemId())) {
				rejectedIndexes.add(index);
			}
		}
		if (!illustrationCases.isEmpty()) {
			Set<Long> published = new HashSet<>(
				legalCaseRepository.findPublishedIdsIn(new HashSet<>(illustrationCases.values())));
			illustrationCases.forEach((index, caseId) -> {
				if (!published.contains(caseId)) {
					rejectedIndexes.add(index);
				}
			});
		}
		List<FieldErrorDetail> rejected = new ArrayList<>();
		rejectedIndexes.forEach(index -> rejected.add(new FieldErrorDetail("items[" + index + "].itemId", NOT_COLLECTIBLE)));
		return rejected;
	}
}
