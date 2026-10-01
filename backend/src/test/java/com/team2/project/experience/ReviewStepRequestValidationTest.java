package com.team2.project.experience;

import static org.assertj.core.api.Assertions.assertThat;
import com.team2.project.experience.dto.ReviewStepRequest;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class ReviewStepRequestValidationTest {
	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	@AfterAll
	static void closeFactory() { FACTORY.close(); }
	@Test
	void validate_nullStep_hasViolation() {
		assertThat(FACTORY.getValidator().validate(new ReviewStepRequest(null))).hasSize(1);
	}
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(ints = {2, 3, 4})
	void validate_stepInRange_passes(int step) {
		assertThat(FACTORY.getValidator().validate(new ReviewStepRequest(step))).isEmpty();
	}
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 5})
	void validate_stepOutOfRange_hasViolation(int step) {
		assertThat(FACTORY.getValidator().validate(new ReviewStepRequest(step))).hasSize(1);
	}
}
