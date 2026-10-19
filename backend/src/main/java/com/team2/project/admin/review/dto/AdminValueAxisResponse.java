package com.team2.project.admin.review.dto;

import com.team2.project.legalcase.domain.ValueAxis;
import com.team2.project.legalcase.domain.ValueAxisStatus;

/** A-12 가치관 축 확정 결과 */
public record AdminValueAxisResponse(Long factorId, ValueAxis valueAxis, ValueAxisStatus valueAxisStatus) {
}
