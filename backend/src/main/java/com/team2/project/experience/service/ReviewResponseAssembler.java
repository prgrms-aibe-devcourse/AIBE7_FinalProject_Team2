package com.team2.project.experience.service;

import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.dto.ReviewResponse;
import com.team2.project.legalcase.domain.CaseSection;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyRangeText;
import com.team2.project.legalcase.domain.PenaltyRule;
import com.team2.project.legalcase.domain.SectionStage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** DB에 접근하지 않고 이미 조회한 사건 정보에서 공개된 단계만 조립한다. */
@Component
public class ReviewResponseAssembler {
	private static final String ALLOWED_RANGE_NOTE = "감경·가중 사유를 반영해 법률상 선고할 수 있는 가장 넓은 범위예요.";
	private static final List<SectionStage> STAGES = List.of(SectionStage.DETAIL, SectionStage.ARGUMENT, SectionStage.LAW);

	public ReviewResponse assemble(ExperienceStatus status, int lastStep, LegalCase legalCase,
		List<CaseSection> sections, List<PenaltyRule> rules) {
		Integer openStep = openStep(lastStep);
		int visibleThrough = openStep == null ? 4 : openStep;
		List<ReviewResponse.Section> visible = new ArrayList<>();
		visible.add(new ReviewResponse.Section(1, "OVERVIEW", lastStep >= 1,
			List.of(new ReviewResponse.Item("OVERVIEW", "사건 개요", legalCase.getOverview(), null))));
		for (int step = 2; step <= visibleThrough; step++) {
			SectionStage stage = STAGES.get(step - 2);
			var items = sections.stream().filter(section -> section.getStage() == stage)
				.filter(section -> stage != SectionStage.LAW || !"LAW_TERM".equals(section.getSectionType()))
				.sorted(Comparator.comparingInt(CaseSection::getDisplayOrder))
				.map(section -> new ReviewResponse.Item(section.getSectionType(), section.getTitle(), section.getContent(), section.getData()))
				.toList();
			visible.add(new ReviewResponse.Section(step, stage.name(), step <= lastStep, items));
		}
		return new ReviewResponse(status, lastStep, openStep, List.copyOf(visible),
			IntStream.rangeClosed(visibleThrough + 1, 4).boxed().toList(),
			visibleThrough == 4 ? law(legalCase, sections, rules) : null,
			status == ExperienceStatus.REVIEWED
				? data(sections, SectionStage.SUMMARY, "SUMMARY").stream().map(String.class::cast).toList() : null);
	}

	public static Integer openStep(int lastStep) {
		return lastStep == 4 ? null : lastStep + 1;
	}

	private ReviewResponse.Law law(LegalCase legalCase, List<CaseSection> sections, List<PenaltyRule> rules) {
		var ranges = rules.stream().sorted(Comparator.comparingInt(PenaltyRule::getDisplayOrder))
			.map(rule -> new ReviewResponse.AllowedRange(rule.getPenaltyType(), rule.getAllowedMin(), rule.getAllowedMax(),
				PenaltyRangeText.format(rule.getPenaltyType(), rule.getAllowedMin(), rule.getAllowedMax()))).toList();
		return new ReviewResponse.Law(legalCase.getAppliedLaw(), legalCase.getStatutoryPenaltyText(), ranges, ALLOWED_RANGE_NOTE,
			new ReviewResponse.Recommended(legalCase.getRecommendedMinMonths(), legalCase.getRecommendedMaxMonths(), legalCase.getRecommendedBasis()),
			data(sections, SectionStage.LAW, "LAW_TERM"));
	}

	private List<Object> data(List<CaseSection> sections, SectionStage stage, String type) {
		return sections.stream().filter(section -> section.getStage() == stage && type.equals(section.getSectionType()))
			.sorted(Comparator.comparingInt(CaseSection::getDisplayOrder))
			.flatMap(section -> section.getData() == null ? java.util.stream.Stream.empty() : section.getData().stream()).toList();
	}
}
