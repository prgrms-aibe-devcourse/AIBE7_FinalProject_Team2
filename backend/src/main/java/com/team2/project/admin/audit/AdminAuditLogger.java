package com.team2.project.admin.audit;

import com.team2.project.admin.domain.AdminAccount;
import com.team2.project.admin.repository.AdminAccountRepository;
import com.team2.project.admin.service.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 감사 로그 기록 (확장 단계, BE-68 · WBS 7.4.5)
 *
 * 저장이 실패하면 어떻게 하나 — 두 가지로 나눈다.
 * - {@link #record}: 상태를 바꾸는 일(로그인 성공, 검수 API A-9 ~ A-13)과 원문 열람(A-7). 부르는 쪽 트랜잭션에 함께 들어가므로
 *   기록이 실패하면 그 일도 되돌린다. "기록 없는 공개 · 승인"이 생기지 않게 한다. 트랜잭션 밖에서 부르면 기록만 따로 저장한다.
 * - {@link #recordQuietly} · {@link #recordLoginFailure}: 되돌릴 일이 없는 기록(로그아웃 · 로그인 실패). 별도 트랜잭션에 저장하고,
 *   실패해도 응답을 바꾸지 않는다. 로그인 실패 응답이 기록 실패로 500이 되면 오히려 정보가 샌다.
 *   저장 실패는 서버 로그에 예외 종류만 남긴다 — DB 오류 메시지("Failing row contains …")에 값이 그대로 들어 있어서다.
 *
 * 로그인 실패는 입력한 이메일을 그대로 남기지 않는다: 이메일 칸에 비밀번호를 잘못 넣으면 평문으로 남기 때문이다.
 * 입력이 실제 관리자 계정과 일치할 때만 그 계정(ID · 이메일)을 남기고, 아니면 누구인지 없이 남긴다.
 * 계정 조회는 모든 실패에 똑같이 한 번 하므로 실패 사유별 응답 시간 차이를 만들지 않는다(BE-67).
 *
 * IP는 request.getRemoteAddr(). 운영에서 Nginx 뒤에 있으면 실제 사용자 IP를 받으려면 forward 헤더 설정이 필요하다(7.1, 기술 스택 3장).
 */
@Slf4j
@Service
public class AdminAuditLogger {

	private final AdminAuditLogRepository repository;
	private final AdminAccountRepository accounts;
	private final TransactionTemplate separateTransaction;

	public AdminAuditLogger(AdminAuditLogRepository repository, AdminAccountRepository accounts,
		PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.accounts = accounts;
		this.separateTransaction = new TransactionTemplate(transactionManager);
		this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** 로그인한 관리자의 행동을 남긴다. 저장이 실패하면 예외를 던져 부르는 쪽 일도 되돌린다 */
	@Transactional
	public void record(AdminPrincipal admin, AdminAuditAction action, AuditTargetType targetType, Long targetId,
		Map<String, Object> detail, HttpServletRequest request) {
		repository.save(AdminAuditLog.of(action, admin.adminId(), AdminAccount.normalizeEmail(admin.email()),
			targetType, targetId, detail, clientIp(request)));
	}

	/** 되돌릴 일이 없는 기록(로그아웃 등, 로그인한 관리자). 별도 트랜잭션, 실패해도 예외를 던지지 않는다 */
	public void recordQuietly(AdminPrincipal admin, AdminAuditAction action, HttpServletRequest request) {
		String ip = clientIp(request);
		saveQuietly(action, () -> AdminAuditLog.of(action, admin.adminId(), AdminAccount.normalizeEmail(admin.email()),
			null, null, null, ip));
	}

	/**
	 * 로그인 실패. 입력 이메일이 실제 계정과 일치하면 그 계정을, 아니면 누구인지 없이 남긴다(입력값 원문은 남기지 않는다).
	 * 별도 트랜잭션, 실패해도 예외를 던지지 않는다
	 */
	public void recordLoginFailure(String inputEmail, HttpServletRequest request) {
		String ip = clientIp(request);
		saveQuietly(AdminAuditAction.LOGIN_FAILURE, () -> accounts.findByEmail(AdminAccount.normalizeEmail(inputEmail))
			.map(account -> AdminAuditLog.of(AdminAuditAction.LOGIN_FAILURE, account.getId(), account.getEmail(),
				null, null, null, ip))
			.orElseGet(() -> AdminAuditLog.of(AdminAuditAction.LOGIN_FAILURE, null, null,
				null, null, Map.of("knownAccount", false), ip)));
	}

	private void saveQuietly(AdminAuditAction action, Supplier<AdminAuditLog> entry) {
		try {
			separateTransaction.executeWithoutResult(status -> repository.save(entry.get()));
		} catch (RuntimeException e) {
			// 예외 메시지 · 원인은 찍지 않는다(DB 오류 메시지에 저장하려던 값이 들어 있다)
			log.warn("감사 로그 저장 실패: action={}, error={}", action, e.getClass().getSimpleName());
		}
	}

	private static String clientIp(HttpServletRequest request) {
		return request == null ? null : request.getRemoteAddr();
	}
}
