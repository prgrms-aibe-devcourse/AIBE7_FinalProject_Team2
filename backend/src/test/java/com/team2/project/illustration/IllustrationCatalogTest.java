package com.team2.project.illustration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team2.project.illustration.dto.IllustrationSummary;
import com.team2.project.illustration.service.IllustrationCatalog;

/** 일러스트 목록 (BE-142) */
class IllustrationCatalogTest {

	// 테스트는 backend 폴더에서 돈다(Gradle · CI). 이미지는 저장소의 frontend 쪽에 있다
	private static final Path IMAGES = Path.of("..", "frontend", "public", "images");

	@Test
	@DisplayName("종류별 개수의 합이 총 개수이고, 사건 컷 · 캐릭터 아이콘이 들어 있다")
	void summary_countsByKind() {
		IllustrationSummary summary = new IllustrationCatalog().summary();

		assertThat(summary.total()).isPositive();
		assertThat(summary.byKind().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(summary.total());
		assertThat(summary.byKind()).containsKeys("cases", "characters/icon/cropped", "characters/icon/generated");
		assertThat(summary.byKind()).doesNotContainKey("");	// 루트(대체 SVG · 배경)는 세지 않는다
	}

	@Test
	@DisplayName("종류는 파일이 든 폴더이고, 폴더 없는 파일은 빈 종류로 센다")
	void summarize_groupsByFolder() {
		IllustrationSummary summary = IllustrationCatalog.summarize(
				List.of("cases/a.webp", "cases/b.webp", "characters/icon/x.webp", "root.webp"));

		assertThat(summary.total()).isEqualTo(4);
		assertThat(summary.byKind()).containsEntry("cases", 2).containsEntry("characters/icon", 1).containsEntry("", 1);
	}

	@Test
	@DisplayName("목록 파일이 이미지 폴더(cases · characters 아래 .webp)와 같다 — 어긋나면 tools/illustrations/update-manifest.sh 실행")
	void manifest_matchesImageFolder() throws IOException {
		assertThat(IMAGES).as("frontend/public/images (backend 폴더 기준 상대 경로)").isDirectory();
		List<String> onDisk = Stream.of("cases", "characters")
				.flatMap(folder -> walk(IMAGES.resolve(folder)))
				.map(path -> IMAGES.relativize(path).toString().replace('\\', '/'))
				.sorted()
				.toList();

		List<String> listed = IllustrationCatalog.read().stream().sorted().toList();

		assertThat(listed)
				.as("illustration-images.txt와 이미지 폴더가 다름. 이미지를 더하거나 지웠으면 tools/illustrations/update-manifest.sh를 실행해 목록을 갱신하세요")
				.containsExactlyElementsOf(onDisk);
		assertThat(listed).doesNotHaveDuplicates();
	}

	private static Stream<Path> walk(Path folder) {
		try {
			return Files.walk(folder).filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".webp"));
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
