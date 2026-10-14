package com.team2.project.personality.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판결 성향 16유형 콘텐츠 (확장 단계, BE-69). 시드로 넣는다(조회 전용).
 * code(INTP 등)는 내부 키로만 쓰고 화면 · 공유 주소 · 공유 응답에는 내보내지 않는다(상표 우려, #119).
 * 공유 주소는 shareId다(시드로 고정, 바꾸지 않는다).
 */
@Entity
@Table(name = "personality_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalityType {

	@Id
	private String code;

	private String shareId;

	private String name;				// 유형명 (예: 그럴 수도 있지형)

	@Enumerated(EnumType.STRING)
	@Column(name = "character_code")
	private PersonalityCharacter character;

	private String oneLiner;

	private String meme;				// 밈 문구 (결과 카드 UI 텍스트)

	private String dailyDescription;	// ① 일상 가치관

	private String verdictDescription;	// ② 판결 연계

	private int tolerance;				// 관용 지수 0 ~ 4 (I · N · F · P 개수, DB CHECK)

	private String imageUrl;			// 캐릭터 이미지 (7.6.8 전에는 null)
}
