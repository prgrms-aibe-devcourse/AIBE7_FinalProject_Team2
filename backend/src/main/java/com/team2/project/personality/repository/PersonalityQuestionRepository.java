package com.team2.project.personality.repository;

import com.team2.project.personality.domain.PersonalityQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalityQuestionRepository extends JpaRepository<PersonalityQuestion, Long> {

	/** 출제할 문항 (활성만, 출제 순서) */
	List<PersonalityQuestion> findAllByActiveTrueOrderByDisplayOrderAsc();
}
