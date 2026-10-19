package com.team2.project.personality.repository;

import com.team2.project.personality.domain.PersonalityChoice;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalityChoiceRepository extends JpaRepository<PersonalityChoice, Long> {

	/** 여러 문항의 선택지를 한 번에 (문항 · 칸 순서) */
	List<PersonalityChoice> findAllByQuestion_IdInOrderByQuestion_IdAscDisplayOrderAsc(Collection<Long> questionIds);
}
