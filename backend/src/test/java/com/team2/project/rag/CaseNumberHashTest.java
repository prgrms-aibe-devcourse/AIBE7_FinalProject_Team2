package com.team2.project.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team2.project.rag.domain.CaseNumberHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

	@Test
	@DisplayName("병합 사건번호는 번호마다 해시한다 (Python rag_store.case_number_hashes와 같은 값)")
	void ofAll_splitsMergedCaseNumbers() {
		String first = "60fe41a008d447748d3f5a631ff4658750ffe8e57ee04fd469e18d39e4933fb2";   // 2014고13
		String second = "db9dca5de1272602bf9b65d7e5188aa4ea4ea3de28531ae544bbdfd7e11ea0d4";  // 2014고14

		assertThat(CaseNumberHash.ofAll("2014고13, 2014고14(병합)")).containsExactly(first, second);
		assertThat(CaseNumberHash.ofAll("2014고13")).containsExactly(first);
		assertThat(CaseNumberHash.ofAll("2018고합\u00a0123"))
			.containsExactly("a8bd8d62083b169002e8561d7268eb0c92984e992a4d9a8474f47339636feab6");
		assertThat(CaseNumberHash.ofAll("2008재고합1"))
			.containsExactly("c0e69c0f138237ce2ba1a7e943630534213250ef9211ea91bfae76b9d0e92299");
		// 사건번호 형식이 아니면 칸 전체를 하나로 해시한다
		assertThat(CaseNumberHash.ofAll("특수번호-1"))
			.containsExactly("702026633b8321f218ee303fd9723e22feb394693af7371290a045944d1b7688");
		assertThat(CaseNumberHash.ofAll(" ")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "  "})
	@DisplayName("빈 사건번호는 거절한다")
	void blank_isRejected(String caseNumber) {
		assertThatThrownBy(() -> CaseNumberHash.of(caseNumber)).isInstanceOf(IllegalArgumentException.class);
	}
}
