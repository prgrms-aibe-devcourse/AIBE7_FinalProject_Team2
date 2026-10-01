package com.team2.project.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.dto.ReviewResponse;
import com.team2.project.experience.service.ReviewResponseAssembler;
import com.team2.project.legalcase.domain.CaseSection;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyRule;
import com.team2.project.legalcase.domain.PenaltyType;
import com.team2.project.legalcase.domain.SectionStage;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class ReviewResponseAssemblerTest {
	private final ReviewResponseAssembler assembler = new ReviewResponseAssembler();
	private final JsonMapper mapper = JsonMapper.builder().build();
	private LegalCase legalCase;
	private List<CaseSection> sections;
	private List<PenaltyRule> rules;

	@BeforeEach
	void setUp() {
		legalCase = mock(LegalCase.class);
		when(legalCase.getOverview()).thenReturn("사건 개요 본문");
		when(legalCase.getAppliedLaw()).thenReturn("형법 제250조 제1항");
		when(legalCase.getStatutoryPenaltyText()).thenReturn("사형, 무기 또는 5년 이상의 징역");
		when(legalCase.getRecommendedMinMonths()).thenReturn(84);
		when(legalCase.getRecommendedMaxMonths()).thenReturn(144);
		when(legalCase.getRecommendedBasis()).thenReturn("비공개 산출 근거");
		sections = List.of(
			section(SectionStage.SUMMARY, "SUMMARY", 1, null, List.of("최종 요약")),
			section(SectionStage.ARGUMENT, "DEFENSE", 2, "변호인 주장 비밀", null),
			section(SectionStage.LAW, "LAW_TERM", 1, null, List.of(Map.of("term", "용어", "desc", "용어 설명 비밀"))),
			section(SectionStage.DETAIL, "DAMAGE", 2, null, List.of(Map.of("label", "피해자", "value", "1명"))),
			section(SectionStage.ARGUMENT, "PROSECUTOR", 1, "검사 주장 비밀", null),
			section(SectionStage.DETAIL, "FACTS", 1, "사실관계", null),
			section(SectionStage.LAW, "OTHER_LAW", 2, "법률 본문 비밀", null));
		rules = List.of(rule(PenaltyType.PRISON, 30, 360, 3), rule(PenaltyType.LIFE, 120, 600, 2), rule(PenaltyType.DEATH, 240, 600, 1));
	}

	private CaseSection section(SectionStage stage, String type, int order, String content, List<Object> data) {
		CaseSection section = mock(CaseSection.class);
		when(section.getStage()).thenReturn(stage);
		when(section.getSectionType()).thenReturn(type);
		when(section.getDisplayOrder()).thenReturn(order);
		when(section.getContent()).thenReturn(content);
		when(section.getData()).thenReturn(data);
		return section;
	}
	private PenaltyRule rule(PenaltyType type, long min, long max, int order) {
		PenaltyRule rule = mock(PenaltyRule.class);
		when(rule.getPenaltyType()).thenReturn(type);
		when(rule.getAllowedMin()).thenReturn(min);
		when(rule.getAllowedMax()).thenReturn(max);
		when(rule.getDisplayOrder()).thenReturn(order);
		return rule;
	}
	private ReviewResponse response(ExperienceStatus status, int last) {
		return assembler.assemble(status, last, legalCase, sections, rules);
	}

	@ParameterizedTest
	@CsvSource({"PRE_JUDGED,1,2", "REVIEWING,2,3", "REVIEWING,3,4", "REVIEWED,4,4"})
	void assemble_progress_exposesOnlyOpenSections(ExperienceStatus status, int last, int visible) {
		var result = response(status, last);
		assertThat(result.status()).isEqualTo(status);
		assertThat(result.lastReviewedStep()).isEqualTo(last);
		assertThat(result.openStep()).isEqualTo(last == 4 ? null : Integer.valueOf(last + 1));
		assertThat(result.sections()).extracting(ReviewResponse.Section::step).containsExactlyElementsOf(IntStream.rangeClosed(1, visible).boxed().toList());
		assertThat(result.sections()).allSatisfy(section -> assertThat(section.confirmed()).isEqualTo(section.step() <= last));
		assertThat(result.lockedSteps()).containsExactlyElementsOf(IntStream.rangeClosed(visible + 1, 4).boxed().toList());
		assertThat(result.law() != null).isEqualTo(visible == 4);
		assertThat(result.summary() != null).isEqualTo(status == ExperienceStatus.REVIEWED);
	}

	@Test
	void assemble_lockedSections_doesNotLeakInJson() {
		String json = mapper.writeValueAsString(response(ExperienceStatus.PRE_JUDGED, 1));
		assertThat(json).doesNotContain("검사 주장 비밀", "변호인 주장 비밀", "용어 설명 비밀", "법률 본문 비밀", "최종 요약", "비공개 산출 근거");
		var tree = mapper.readTree(json);
		assertThat(tree.has("law")).isTrue();
		assertThat(tree.get("law").isNull()).isTrue();
		assertThat(tree.get("summary").isNull()).isTrue();
	}

	@Test
	void assemble_lawTermsAndSortedItems_doNotDuplicate() {
		var result = response(ExperienceStatus.REVIEWED, 4);
		assertThat(result.sections().get(1).items()).extracting(ReviewResponse.Item::sectionType).containsExactly("FACTS", "DAMAGE");
		assertThat(result.sections().get(2).items()).extracting(ReviewResponse.Item::sectionType).containsExactly("PROSECUTOR", "DEFENSE");
		assertThat(result.sections().get(3).items()).extracting(ReviewResponse.Item::sectionType).containsExactly("OTHER_LAW");
		assertThat(result.law().terms()).containsExactly(Map.of("term", "용어", "desc", "용어 설명 비밀"));
		assertThat(result.summary()).containsExactly("최종 요약");
	}

	@Test
	void assemble_ranges_reusesApiEightText() {
		var law = response(ExperienceStatus.REVIEWED, 4).law();
		assertThat(law.allowedRanges()).extracting(ReviewResponse.AllowedRange::text).containsExactly(
			"사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)", "무기징역 (감경하면 징역 10년 ~ 50년)", "징역 2년 6개월 ~ 30년");
		assertThat(law.recommended().minMonths()).isEqualTo(84);
		assertThat(law.recommended().maxMonths()).isEqualTo(144);
		assertThat(mapper.writeValueAsString(law)).doesNotContain("allowedBasis");
	}

	@Test
	void assemble_nullItemFields_omitsOnlyItemNulls() {
		var tree = mapper.valueToTree(response(ExperienceStatus.PRE_JUDGED, 1));
		var items = tree.get("sections").get(1).get("items");
		assertThat(items.get(0).has("data")).isFalse();
		assertThat(items.get(0).get("content").asText()).isEqualTo("사실관계");
		assertThat(items.get(1).has("content")).isFalse();
		assertThat(items.get(1).get("data").isArray()).isTrue();
	}

	@Test
	void assemble_noLawTerms_returnsEmptyList() {
		sections = sections.stream().filter(section -> !section.getSectionType().equals("LAW_TERM")).toList();
		assertThat(response(ExperienceStatus.REVIEWED, 4).law().terms()).isEmpty();
	}
}
