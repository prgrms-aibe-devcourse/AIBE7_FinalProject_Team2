package com.team2.project.level.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 경험치 지급 원장 (BE-106, V30). 한 줄 = 한 번의 지급.
 * 같은 대상 중복을 조용히 무시해야 해서 엔티티 save()가 아니라 XpEventRepository.insertIfAbsent(ON CONFLICT DO NOTHING)로 넣는다.
 * 엔티티는 스키마 검증(ddl-auto: validate)용이다
 */
@Entity
@Table(name = "xp_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class XpEvent {

	@Id
	private Long id;
}
