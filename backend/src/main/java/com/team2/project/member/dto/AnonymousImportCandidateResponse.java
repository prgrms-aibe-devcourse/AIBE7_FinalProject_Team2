package com.team2.project.member.dto;

/** M-9 응답: 이 브라우저의 익명 ID에 쌓인 체험 수 · 첫인상 엽서 수 (BE-140) */
public record AnonymousImportCandidateResponse(long experienceCount, long preJudgmentCount) {
}
