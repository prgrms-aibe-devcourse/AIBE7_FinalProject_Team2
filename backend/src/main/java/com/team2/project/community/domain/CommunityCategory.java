package com.team2.project.community.domain;

import java.util.Arrays;
import java.util.Optional;

/** 커뮤니티 글 말머리 (API 명세 12장, BE-115) */
public enum CommunityCategory {

	VERDICT,	// 판결 공유
	QUESTION,	// 질문
	FREE;		// 자유

	public static Optional<CommunityCategory> from(String value) {
		return Arrays.stream(values()).filter(category -> category.name().equals(value)).findFirst();
	}
}
