package com.team2.project.personality.dto;

import com.team2.project.personality.domain.PersonalityCharacter;
import com.team2.project.personality.domain.PersonalityType;

/**
 * 유형 공통 형식 (API 명세 9-1). 4글자 코드는 넣지 않고 shareId로만 구분한다(#119)
 */
public record PersonalityTypeResponse(
	String shareId,
	String name,
	PersonalityCharacter character,
	int tolerance,
	String oneLiner,
	String meme,
	String dailyDescription,
	String verdictDescription,
	String imageUrl) {

	public static PersonalityTypeResponse from(PersonalityType type) {
		return new PersonalityTypeResponse(type.getShareId(), type.getName(), type.getCharacter(), type.getTolerance(),
			type.getOneLiner(), type.getMeme(), type.getDailyDescription(), type.getVerdictDescription(), type.getImageUrl());
	}
}
