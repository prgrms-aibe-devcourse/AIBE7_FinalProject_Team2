package com.team2.project.personality.domain;

import com.team2.project.legalcase.domain.ValueAxis;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판결 성향 테스트 문항 (확장 단계, BE-69 · WBS 7.6.3)
 * 상황 문장 + 두 행동(A · B) 문장. 5칸의 "매우 · 조금 · 보통"은 화면이 붙인다. 콘텐츠는 시드로 넣는다(조회 전용).
 */
@Entity
@Table(name = "personality_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalityQuestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	private ValueAxis axis;			// factor.value_axis와 같은 키

	@Enumerated(EnumType.STRING)
	private PersonalityScenario scenario;

	private String content;			// 상황 문장

	// 끝 글자가 대문자 하나라 기본 이름 규칙으로는 option_a가 아니라 optiona가 된다
	@Column(name = "option_a")
	private String optionA;			// 왼쪽 끝 행동 (매우 A · 조금 A)

	@Column(name = "option_b")
	private String optionB;			// 오른쪽 끝 행동 (조금 B · 매우 B)

	private int displayOrder;		// 출제 순서

	@Column(name = "is_active")
	private boolean active;
}
