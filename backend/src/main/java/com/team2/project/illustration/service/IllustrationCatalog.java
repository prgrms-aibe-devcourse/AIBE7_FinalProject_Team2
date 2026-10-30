package com.team2.project.illustration.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.team2.project.illustration.dto.IllustrationSummary;

/**
 * 일러스트 목록 (BE-142). 이미지는 프론트({@code frontend/public/images})에만 있고 백엔드는 갖고 있지 않아,
 * 클래스패스의 {@value #MANIFEST} 한 줄 한 장(예: {@code cases/case-1-origin.webp})으로 센다.
 * 목록은 {@code tools/illustrations/update-manifest.sh}로 만들고, 이미지 폴더와 어긋나면 IllustrationCatalogTest가 실패한다.
 *
 * <p><b>저장소 기준이다 — 가능한 한 DB 기준으로 저장소를 최신화한다.</b> 사건 사진은 DB 저장값(관리자 업로드)이 규칙 주소보다 우선이지만(BE-141),
 * 이 목록은 DB를 보지 않고 공개 여부도 따지지 않는다(비공개 사건 컷 · 공용 컷 포함, DB에만 있는 이미지는 미포함).
 * 관리자가 DB(S3)에 사진을 올릴 때 같은 파일을 저장소에도 함께 올리고 목록을 갱신하면 어긋나지 않는다. 조금 다른 것은 감수한다.
 * DB 기준으로 바꾸는 일은 필수가 아닌 후속 이슈(BE-143)다.
 */
@Component
public class IllustrationCatalog {

	public static final String MANIFEST = "illustration-images.txt";

	private final IllustrationSummary summary;

	public IllustrationCatalog() {
		this.summary = summarize(read());
	}

	public IllustrationSummary summary() {
		return summary;
	}

	/** 목록 → 총 개수 · 종류별 개수. 종류는 파일이 들어 있는 폴더 경로(폴더 없이 루트에 있는 파일은 종류 {@code ""}) */
	public static IllustrationSummary summarize(List<String> paths) {
		Map<String, Integer> byKind = new TreeMap<>();
		for (String path : paths) {
			int slash = path.lastIndexOf('/');
			byKind.merge(slash < 0 ? "" : path.substring(0, slash), 1, Integer::sum);
		}
		return new IllustrationSummary(paths.size(), byKind);
	}

	/** 목록 파일의 경로들(빈 줄 제외). 테스트가 이미지 폴더와 견주는 데도 쓴다 */
	public static List<String> read() {
		try {
			String text = new ClassPathResource(MANIFEST).getContentAsString(StandardCharsets.UTF_8);
			return text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(MANIFEST + "를 읽지 못했습니다", e);
		}
	}
}
