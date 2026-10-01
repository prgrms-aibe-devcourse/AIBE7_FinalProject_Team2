package com.team2.project.judgment;

import static org.assertj.core.api.Assertions.assertThat;

import com.team2.project.legalcase.domain.PenaltyRangeText;
import com.team2.project.legalcase.domain.PenaltyType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PenaltyRangeTextTest {
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"PRISON||360|징역", "PRISON|30||징역", "PRISON|||징역",
		"FINE||20000000|벌금", "FINE|25000||벌금", "FINE|||벌금",
		"DEATH||600|사형", "DEATH|240||사형", "DEATH|||사형",
		"LIFE||600|무기징역", "LIFE|120||무기징역", "LIFE|||무기징역"
	})
	void format_missingBound_returnsOnlyPenaltyName(PenaltyType type, Long min, Long max, String expected) {
		assertThat(PenaltyRangeText.format(type, min, max)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"PRISON|30|360|징역 2년 6개월 ~ 30년",
		"PRISON|1|120|징역 1개월 ~ 10년",
		"LIFE|120|600|무기징역 (감경하면 징역 10년 ~ 50년)",
		"DEATH|240|600|사형 (감경하면 무기징역 또는 징역 20년 ~ 50년)",
		"FINE|25000|20000000|벌금 2만 5천 원 ~ 2천만 원"
	})
	void format_penaltyRanges_matchSpecification(PenaltyType type, long min, long max, String expected) {
		assertThat(PenaltyRangeText.format(type, min, max)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({"0,0개월", "12,1년", "13,1년 1개월"})
	void formatMonths_boundaries_omitZeroParts(long value, String expected) {
		assertThat(PenaltyRangeText.formatMonths(value)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {"0|0 원", "5000000|500만 원", "100000000|1억 원", "123456789|1억 2,345만 6,789 원"})
	void formatMoney_groups_matchSpecification(long value, String expected) {
		assertThat(PenaltyRangeText.formatMoney(value)).isEqualTo(expected);
	}
}
