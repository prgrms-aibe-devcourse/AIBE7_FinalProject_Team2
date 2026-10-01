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
	@Test
	void validate_stepThree_passes() {
		assertThat(FACTORY.getValidator().validate(new ReviewStepRequest(3))).isEmpty();
	}
}
