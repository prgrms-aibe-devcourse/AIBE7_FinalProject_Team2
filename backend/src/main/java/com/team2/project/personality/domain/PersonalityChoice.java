package com.team2.project.personality.domain;

import com.team2.project.legalcase.domain.ValueAxis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성향 테스트 선택지 (확장 단계, BE-69). 문항마다 5개: 매우 A · 조금 A · 보통 · 조금 B · 매우 B (display_order 1 ~ 5)
 * 보통은 letter = null, score = 0. 나머지는 그 축의 글자와 1(조금) · 2(매우)점 (성향 산식 2-2).
 * axis는 문항의 축을 복제한 값이다 — DB가 복합 FK(question_id, axis)와 CHECK로 "그 축의 글자만" 보장한다.
 */
@Entity
@Table(name = "personality_choice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalityChoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "question_id")
	private PersonalityQuestion question;

	@Enumerated(EnumType.STRING)
	@Column(name = "axis")
	private ValueAxis axis;

	private String letter;			// 보통이면 null

	private int score;				// 0(보통) · 1(조금) · 2(매우)

	private int displayOrder;		// 1 ~ 5

	/** 보통(가운데 칸)인지 */
	public boolean isNeutral() {
		return letter == null;
	}
}
