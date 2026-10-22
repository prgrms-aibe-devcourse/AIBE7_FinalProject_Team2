package com.team2.project.admin.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.common.exception.BusinessException;
import com.team2.project.common.exception.ErrorCode;
import com.team2.project.imagegen.service.CasePaperService;
import com.team2.project.legalcase.domain.CaseStatus;
import com.team2.project.legalcase.domain.LegalCase;
import com.team2.project.legalcase.repository.CaseIncidentPhotoRepository;
import com.team2.project.legalcase.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 신문 지면 생성의 트랜잭션 · 잠금 경계 (BE-92, PR #212 리뷰). Gemini 호출(generateContent)은 잠금 밖에서
 * 끝내므로, 그 사이 다른 관리자가 사건을 공개(PUBLISHED)하면 저장 직전 재확인에서 막혀야 한다
 */
class AdminCasePaperServiceTest {

	private LegalCaseRepository caseRepository;
	private CasePaperService paperService;
	private AdminAuditLogger auditLogger;
	private AdminCasePaperService service;

	@BeforeEach
	void setUp() {
		caseRepository = mock(LegalCaseRepository.class);
		paperService = mock(CasePaperService.class);
		CaseIncidentPhotoRepository incidentPhotoRepository = mock(CaseIncidentPhotoRepository.class);
		auditLogger = mock(AdminAuditLogger.class);
		service = new AdminCasePaperService(caseRepository, paperService, incidentPhotoRepository, auditLogger,
			new NoOpTransactionManager());
	}

	@Test
	@DisplayName("Gemini 호출 중(잠금 밖) 다른 요청이 사건을 공개하면, 저장 직전 재확인에서 INVALID_STATE로 막는다")
	void publishedWhileGenerating_failsOnSaveRecheck() {
		LegalCase draft = legalCase(1L, CaseStatus.DRAFT);
		LegalCase publishedByRace = legalCase(1L, CaseStatus.PUBLISHED);
		when(caseRepository.findById(1L)).thenReturn(Optional.of(draft));
		when(caseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(publishedByRace));
		when(paperService.generateContent(draft))
			.thenReturn(new CasePaperService.GeneratedPaper("헤드라인", null, null, List.of("문단")));
		AdminPrincipal admin = admin();
		HttpServletRequest request = mock(HttpServletRequest.class);

		assertThatThrownBy(() -> service.generatePaper(1L, admin, request)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);

		verifyNoInteractions(auditLogger);
	}

	@Test
	@DisplayName("이미 공개된 사건은 Gemini를 부르기 전에 막는다 (잠금 밖 1차 확인)")
	void alreadyPublished_failsBeforeCallingGemini() {
		LegalCase published = legalCase(1L, CaseStatus.PUBLISHED);
		when(caseRepository.findById(1L)).thenReturn(Optional.of(published));
		AdminPrincipal admin = admin();
		HttpServletRequest request = mock(HttpServletRequest.class);

		assertThatThrownBy(() -> service.generatePaper(1L, admin, request)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE);

		verifyNoInteractions(paperService);
	}

	private static AdminPrincipal admin() {
		return new AdminPrincipal(1L, "admin@example.com", "관리자", null, true);
	}

	private static LegalCase legalCase(Long id, CaseStatus status) {
		try {
			Constructor<LegalCase> constructor = LegalCase.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			LegalCase legalCase = constructor.newInstance();
			setField(legalCase, "id", id);
			setField(legalCase, "status", status);
			return legalCase;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	/** 실제 DB 트랜잭션 없이 TransactionTemplate의 콜백만 그대로 실행하는 가짜 (단위 테스트 전용) */
	static class NoOpTransactionManager implements PlatformTransactionManager {

		@Override
		public TransactionStatus getTransaction(TransactionDefinition definition) {
			return new SimpleTransactionStatus();
		}

		@Override
		public void commit(TransactionStatus status) {
		}

		@Override
		public void rollback(TransactionStatus status) {
		}
	}
}
