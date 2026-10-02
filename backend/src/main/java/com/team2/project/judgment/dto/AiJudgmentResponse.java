package com.team2.project.judgment.dto;

import com.team2.project.judgment.domain.PenaltyDifference;
import java.util.List;

/**
 * API 10. AI 판결 (S-07)
 * 검수해 공개한 AI 판결을 DB에서 읽기만 한다. AI를 호출하지 않는다 (시퀀스 6장).
 * 형량 차이 계산 규칙은 도메인(PenaltyDifference)에 있고, 여기서는 결과만 담는다.
 */
public record AiJudgmentResponse(
	JudgmentView judgment,
	JudgmentView myJudgment,
	PenaltyDifference diffFromMine,
	List<String> references
) { }
