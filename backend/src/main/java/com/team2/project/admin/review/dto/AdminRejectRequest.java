package com.team2.project.admin.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A-10 반려 사유 (1 ~ 500자, judgment.review_note varchar(500)) */
public record AdminRejectRequest(@NotBlank @Size(max = 500) String reason) {
}
