package com.team2.project.admin.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 관리자 감사 로그 한 줄 (확장 단계, BE-68). 한 번 쓰면 고치지 않는다(수정 · 삭제 API 없음).
 * 비밀번호 · 원문 내용 · 토큰은 넣지 않는다.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long adminId;			// 누구인지 모르면(로그인 실패) NULL. 계정이 지워지면 NULL이 된다

	private String actorEmail;		// 그때의 이메일(소문자). 로그인 실패면 입력한 이메일

	@Enumerated(EnumType.STRING)
	private AdminAuditAction action;

	@Enumerated(EnumType.STRING)
	private AuditTargetType targetType;

	private Long targetId;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> detail;

	private String ipAddress;

	@CreationTimestamp
	private Instant createdAt;

	static AdminAuditLog of(AdminAuditAction action, Long adminId, String actorEmail, AuditTargetType targetType,
		Long targetId, Map<String, Object> detail, String ipAddress) {
		AdminAuditLog log = new AdminAuditLog();
		log.action = action;
		log.adminId = adminId;
		log.actorEmail = actorEmail;
		log.targetType = targetType;
		log.targetId = targetId;
		// null 값을 허용한다(A-11 "null = 지움"을 그대로 남기기 위해, #118 리뷰). Map.copyOf는 null 값에서 NPE
		log.detail = detail == null || detail.isEmpty() ? null : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
		log.ipAddress = ipAddress;
		return log;
	}
}
