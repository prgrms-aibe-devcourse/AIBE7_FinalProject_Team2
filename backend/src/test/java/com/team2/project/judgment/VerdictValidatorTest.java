package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team2.project.experience.domain.Experience;
import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import com.team2.project.experience.repository.ExperienceRepository;
import com.team2.project.judgment.domain.InvalidJudgmentException;
import com.team2.project.judgment.domain.InvalidJudgmentException.Reason;
import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.judgment.dto.VerdictRequest.FactorItem;
import com.team2.project.judgment.service.VerdictValidator;
import com.team2.project.judgment.service.VerdictValidator.Option;
import com.team2.project.judgment.service.VerdictService;
import com.team2.project.judgment.repository.JudgmentRepository;
import com.team2.project.judgment.repository.JudgmentFactorRepository;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.domain.PenaltyRule;
import com.team2.project.legalcase.repository.FactorRepository;
import com.team2.project.legalcase.repository.PenaltyRuleRepository;
import com.team2.project.legalcase.domain.PenaltyType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

class VerdictValidatorTest {
	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	private static final List<Option> MURDER = List.of(new Option(PenaltyType.DEATH, 240L, 600L, false),
		new Option(PenaltyType.LIFE, 120L, 600L, false), new Option(PenaltyType.PRISON, 30L, 360L, true));
	private static final List<Option> FRAUD = List.of(new Option(PenaltyType.PRISON, 1L, 120L, true),
		new Option(PenaltyType.FINE, 25000L, 20000000L, true));
	private static final Set<Long> IDS = LongStream.rangeClosed(1, 11).boxed().collect(Collectors.toSet());
	private final VerdictValidator validator = new VerdictValidator();

	@AfterAll
	static void closeFactory() { FACTORY.close(); }

	static VerdictRequest req(String type, String reduced, Integer months, Long fine, Integer suspension, FactorItem... factors) {
		return new VerdictRequest(type, reduced, months, fine, suspension, List.of(factors), null);
	}

	static Stream<Arguments> successes() {
		return Stream.of(
			Arguments.of(req("LIFE", null, null, null, null), MURDER),
			Arguments.of(req("DEATH", null, null, null, null), MURDER),
			Arguments.of(req("DEATH", "LIFE", null, null, null), MURDER),
			Arguments.of(req("DEATH", "PRISON", 240, null, null), MURDER),
			Arguments.of(req("LIFE", "PRISON", 480, null, null), MURDER),
			Arguments.of(req("PRISON", null, 36, null, 24), MURDER),
			Arguments.of(req("PRISON", null, 180, null, null), MURDER),
			Arguments.of(req("PRISON", null, 30, null, 12), MURDER),
			Arguments.of(req("PRISON", null, 36, null, 60), MURDER),
			Arguments.of(req("FINE", null, null, 3000000L, 24), FRAUD),
			Arguments.of(req("LIFE", null, null, null, null, IDS.stream().sorted()
				.map(id -> new FactorItem(id, "DOWN")).toArray(FactorItem[]::new)), MURDER));
	}

	@ParameterizedTest
	@MethodSource("successes")
	void validate_validRequest_returnsEnumsAndValues(VerdictRequest request, List<Option> options) {
		assertThat(FACTORY.getValidator().validate(request)).isEmpty();
		var result = validator.validate(request, options, IDS);
		assertThat(result.penaltyType().name()).isEqualTo(request.penaltyType());
		assertThat(result.reducedTo() == null ? null : result.reducedTo().name()).isEqualTo(request.reducedTo());
		assertThat(result.prisonMonths()).isEqualTo(request.prisonMonths());
		assertThat(result.fineAmount()).isEqualTo(request.fineAmount());
		assertThat(result.suspensionMonths()).isEqualTo(request.suspensionMonths());
		assertThat(result.factors()).hasSize(request.factors().size());
	}

	static Stream<Arguments> failures() {
		return Stream.of(
			failure(req("PRISON", "LIFE", 180, null, null), Reason.INVALID_REDUCTION),
			failure(req("LIFE", "DEATH", null, null, null), Reason.INVALID_REDUCTION),
			failure(req("DEATH", "FINE", null, null, null), Reason.INVALID_REDUCTION),
			failure(req("DEATH", "UNKNOWN", null, null, null), Reason.INVALID_REDUCTION),
			failure(req("FINE", null, null, 100000L, null), Reason.PENALTY_NOT_OFFERED),
			failure(req("NOT_GUILTY", null, null, null, null), Reason.PENALTY_NOT_OFFERED),
			failure(req("LIFE", "PRISON", 119, null, null), Reason.OUT_OF_ALLOWED_RANGE),
			failure(req("LIFE", "PRISON", 601, null, null), Reason.OUT_OF_ALLOWED_RANGE),
			failure(req("PRISON", null, 29, null, null), Reason.OUT_OF_ALLOWED_RANGE),
			failure(req("PRISON", null, 361, null, null), Reason.OUT_OF_ALLOWED_RANGE),
			failure(req("PRISON", null, 0, null, null), Reason.OUT_OF_ALLOWED_RANGE),
			failure(req("LIFE", "PRISON", 120, null, 24), Reason.SUSPENSION_CONDITION),
			failure(req("PRISON", null, 37, null, 24), Reason.SUSPENSION_CONDITION),
			failure(req("PRISON", null, 36, null, 11), Reason.SUSPENSION_CONDITION),
			failure(req("PRISON", null, 36, null, 61), Reason.SUSPENSION_CONDITION),
			failure(req("PRISON", null, 36, null, null, new FactorItem(12L, "UP")), Reason.INVALID_FACTOR),
			failure(req("PRISON", null, 36, null, null, new FactorItem(2L, "UP"), new FactorItem(2L, "DOWN")), Reason.INVALID_FACTOR),
			failure(req("PRISON", null, 36, null, null, new FactorItem(2L, null)), Reason.INVALID_FACTOR),
			failure(req("PRISON", null, 36, null, null, new FactorItem(2L, "SIDE")), Reason.INVALID_FACTOR),
			failure(req("PRISON", "LIFE", 601, null, null), Reason.INVALID_REDUCTION),
			failure(req("PRISON", null, 601, null, 11, new FactorItem(12L, "UP")), Reason.OUT_OF_ALLOWED_RANGE),
			failure(req("PRISON", null, 36, null, 11, new FactorItem(12L, "UP")), Reason.SUSPENSION_CONDITION),
			Arguments.of(req("FINE", null, null, 6000000L, 24), FRAUD, Reason.SUSPENSION_CONDITION, null),
			Arguments.of(req("FINE", null, null, 20000L, null), FRAUD, Reason.OUT_OF_ALLOWED_RANGE, "fineAmount"));
	}

	private static Arguments failure(VerdictRequest request, Reason reason) {
		return Arguments.of(request, MURDER, reason, reason == Reason.OUT_OF_ALLOWED_RANGE ? "prisonMonths" : null);
	}

	@ParameterizedTest
	@MethodSource("failures")
	void validate_invalidRequest_reportsFirstReasonAndField(VerdictRequest request, List<Option> options, Reason reason, String field) {
		assertThat(FACTORY.getValidator().validate(request)).isEmpty();
		assertThatThrownBy(() -> validator.validate(request, options, IDS))
			.isInstanceOfSatisfying(InvalidJudgmentException.class, exception -> {
				assertThat(exception.getReason()).isEqualTo(reason);
				assertThat(exception.getField()).isEqualTo(field);
			});
	}

	@ParameterizedTest
	@ValueSource(strings = {"uk_judgment_experience_timing", "chk_judgment_positive_values", "fk_judgment_experience"})
	void submit_integrityViolation_onlyDuplicateFinalBecomesStateError(String constraint) {
		var experiences = mock(ExperienceRepository.class);
		var rules = mock(PenaltyRuleRepository.class);
		var factors = mock(FactorRepository.class);
		var judgments = mock(JudgmentRepository.class);
		var judgmentFactors = mock(JudgmentFactorRepository.class);
		var experience = mock(Experience.class);
		var legalCase = mock(LegalCase.class);
		var rule = mock(PenaltyRule.class);
		when(experiences.findById(1L)).thenReturn(Optional.of(experience));
		when(experience.getStatus()).thenReturn(ExperienceStatus.REVIEWED);
		when(experience.getLegalCase()).thenReturn(legalCase);
		when(legalCase.getId()).thenReturn(2L);
		when(rule.getPenaltyType()).thenReturn(PenaltyType.LIFE);
		when(rules.findAllByCaseId(2L)).thenReturn(List.of(rule));
		when(factors.findAllByCaseId(2L)).thenReturn(List.of());
		var cause = new ConstraintViolationException("fixture violation", new java.sql.SQLException(), constraint);
		var original = new DataIntegrityViolationException("fixture", cause);
		when(judgments.saveAndFlush(any())).thenThrow(original);
		var service = new VerdictService(experiences, rules, factors, judgments, judgmentFactors, validator);
		var request = req("LIFE", null, null, null, null);
		assertThat(FACTORY.getValidator().validate(request)).isEmpty();
		if (constraint.equals("uk_judgment_experience_timing")) {
			assertThatThrownBy(() -> service.submit(1L, request)).isInstanceOfSatisfying(InvalidExperienceStateException.class,
				error -> assertThat(error.getExperienceStatus()).isEqualTo(ExperienceStatus.VERDICT_CONFIRMED));
		} else {
			assertThatThrownBy(() -> service.submit(1L, request)).isSameAs(original);
		}
		verifyNoInteractions(judgmentFactors);
	}
}
