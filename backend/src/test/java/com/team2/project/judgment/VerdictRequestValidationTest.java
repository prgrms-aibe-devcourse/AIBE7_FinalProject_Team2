package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.judgment.dto.VerdictRequest;
import com.team2.project.judgment.dto.VerdictRequest.FactorItem;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class VerdictRequestValidationTest {
	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = FACTORY.getValidator();

	@AfterAll
	static void closeFactory() { FACTORY.close(); }

	static VerdictRequest request(String type, String reduced, Integer prison, Long fine, Integer suspension,
		List<FactorItem> factors, String opinion) {
		return new VerdictRequest(type, reduced, prison, fine, suspension, factors, opinion);
	}

	static Stream<VerdictRequest> invalidRequests() {
		return Stream.of(
			request("LIFE", null, 120, null, null, null, null),
			request("LIFE", "PRISON", null, null, null, null, null),
			request("PRISON", null, 36, 1000L, null, null, null),
			request("PRISON", null, -1, null, null, null, null),
			request("FINE", null, null, -1L, null, null, null),
			request("PRISON", null, 36, null, -1, null, null),
			request("PRISON", null, 36, null, null, Arrays.asList((FactorItem) null), null),
			request("PRISON", null, 36, null, null, List.of(new FactorItem(null, "UP")), null),
			request("PRISON", null, 36, null, null, null, "x"),
			request(null, null, null, null, null, null, null),
			request(" ", null, null, null, null, null, null),
			request("DEATH", null, null, null, 12, null, null),
			request("DEATH", "LIFE", null, 1L, null, null, null));
	}

	@ParameterizedTest
	@MethodSource("invalidRequests")
	void validate_invalidFormat_hasViolation(VerdictRequest request) {
		assertThat(VALIDATOR.validate(request)).isNotEmpty();
	}

	static Stream<VerdictRequest> validRequests() {
		return Stream.of(
			request("NOT_GUILTY", null, null, null, null, null, null),
			request("PRISON", "LIFE", 180, null, null, null, null),
			request("PRISON", null, 0, null, null, null, null),
			request("DEATH", "UNKNOWN", null, null, null, null, null),
			request("PRISON", null, 36, null, null, List.of(new FactorItem(1L, null)), null),
			request("LIFE", null, null, null, null, null, null));
	}

	@ParameterizedTest
	@MethodSource("validRequests")
	void validate_validFormat_leavesBusinessErrorsForValidator(VerdictRequest request) {
		assertThat(VALIDATOR.validate(request)).isEmpty();
	}

	@Test
	void constructor_nullFactors_normalizesToEmpty() {
		assertThat(request("LIFE", null, null, null, null, null, null).factors()).isEmpty();
	}

	@Test
	void readValue_defaultJackson_reportsNumericCoercion() {
		var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
		var stringValue = mapper.readValue("{\"penaltyType\":\"PRISON\",\"prisonMonths\":\"18\"}", VerdictRequest.class);
		var decimalValue = mapper.readValue("{\"penaltyType\":\"PRISON\",\"prisonMonths\":1.5}", VerdictRequest.class);
		assertThat(stringValue.prisonMonths()).isEqualTo(18);
		assertThat(decimalValue.prisonMonths()).isEqualTo(1);
		System.out.println("Jackson default binding: string 18 -> " + stringValue.prisonMonths()
			+ ", decimal 1.5 -> " + decimalValue.prisonMonths() + "; settings unchanged");
	}
}
