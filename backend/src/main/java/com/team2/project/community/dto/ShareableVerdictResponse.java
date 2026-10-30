package com.team2.project.community.dto;

import com.team2.project.community.domain.SharedVerdict;

/** C-7 글에 붙일 수 있는 내 판결 (가장 최근에 확정한 판결) */
public record ShareableVerdictResponse(long caseId, String caseTitle, SharedVerdict verdict) {
}
