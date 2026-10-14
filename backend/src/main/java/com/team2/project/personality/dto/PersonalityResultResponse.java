package com.team2.project.personality.dto;

import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.personality.domain.PersonalityResult;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 결과 공통 형식 (API 명세 9-1, P-2 · P-3 · P-5). axisScores는 0 ~ 1 관대 쪽 비중
 */
public record PersonalityResultResponse(
	PersonalityTypeResponse type,
	Map<ValueAxis, Double> axisScores,
	OffsetDateTime testedAt) {

	/** API 명세 1-1: 시각은 +09:00 오프셋으로 내려준다 */
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	public static PersonalityResultResponse from(PersonalityResult result) {
		return new PersonalityResultResponse(PersonalityTypeResponse.from(result.getType()), result.getAxisScores(),
			result.getCreatedAt().atZone(KST).toOffsetDateTime());
	}
}
