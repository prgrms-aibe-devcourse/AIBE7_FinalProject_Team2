package com.team2.project.personality.repository;

import com.team2.project.personality.domain.PersonalityType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalityTypeRepository extends JpaRepository<PersonalityType, String> {

	/** 공유 주소(/test/types/{shareId})로 유형 찾기 */
	Optional<PersonalityType> findByShareId(String shareId);
}
