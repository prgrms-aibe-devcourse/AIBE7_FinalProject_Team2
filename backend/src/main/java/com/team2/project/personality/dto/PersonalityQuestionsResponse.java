package com.team2.project.personality.dto;

import com.team2.project.personality.domain.PersonalityQuestion;
import com.team2.project.personality.domain.PersonalityScenario;
import java.util.List;

/**
 * P-1 문항 목록. 축 · 글자 · 점수는 내려 주지 않는다(채점은 서버, API 명세 9-1)
 */
public record PersonalityQuestionsResponse(List<Question> questions) {

	public record Question(Long id, PersonalityScenario scenario, String content, String optionA, String optionB) {

		public static Question from(PersonalityQuestion question) {
			return new Question(question.getId(), question.getScenario(), question.getContent(), question.getOptionA(),
				question.getOptionB());
		}
	}
}
