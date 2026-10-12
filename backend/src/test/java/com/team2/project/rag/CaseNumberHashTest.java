package com.team2.project.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.rag.domain.CaseNumberHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 사건번호 해시가 오프라인 도구(tools/ai-judgment/rag_store.py case_number_hash)와 같은지 확인한다 (BE-51).
 * 기대값은 Python으로 계산한 값이다. 다르면 자기 사건 제외 검색이 판례를 빼지 못해 실제 판결이 AI에게 노출된다.
 */
class CaseNumberHashTest {

	@ParameterizedTest(name = "\"{0}\"")
	@CsvSource(delimiter = '|', value = {
		"2019고합123|224f0fbd872991e0ad2342b207c7f25047abd13f41f935bdc93a74483525090b",
		"2014고13, 2014고14(병합)|ce8f9489fe72424def7fd9fbe36039b3d727074df8e08ac3fc34208db73dad57",
		"' 2009 고합 188 '|aa7a89e6f81ab2dc48b1a37a60e657b408f91b007e2331ac0902c11c2507577f",
		"2018고합\u00a0123|a8bd8d62083b169002e8561d7268eb0c92984e992a4d9a8474f47339636feab6",
		"2018\u3000고합123|a8bd8d62083b169002e8561d7268eb0c92984e992a4d9a8474f47339636feab6",
	})
	@DisplayName("Python rag_store.case_number_hash와 같은 해시를 만든다 (NBSP · 전각 공백 포함 공백 제거 후 SHA-256 hex)")
	void sameAsPythonTool(String caseNumber, String expected) {
		assertThat(CaseNumberHash.of(caseNumber)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "  "})
	@DisplayName("빈 사건번호는 거절한다")
	void blank_isRejected(String caseNumber) {
		assertThatThrownBy(() -> CaseNumberHash.of(caseNumber)).isInstanceOf(IllegalArgumentException.class);
	}
}
