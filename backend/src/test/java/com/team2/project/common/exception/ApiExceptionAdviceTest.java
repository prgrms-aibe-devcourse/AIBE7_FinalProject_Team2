package com.team2.project.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team2.project.experience.domain.ExperienceStatus;
import com.team2.project.experience.domain.InvalidExperienceStateException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외 → 공통 에러 응답(API 명세 1-4 · 1-5) 변환 검증. 시험용 컨트롤러로 예외를 일으킨다
 */
class ApiExceptionAdviceTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new ApiExceptionAdvice())
			.build();
	}

	@Test
	@DisplayName("상태 예외는 409 INVALID_STATE와 currentStatus로 응답한다")
	void handleBusiness_invalidState_returnsCurrentStatus() throws Exception {
		mockMvc.perform(get("/test/invalid-state"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"))
			.andExpect(jsonPath("$.currentStatus").value("PRE_JUDGED"))
			.andExpect(jsonPath("$.details").value(nullValue()));
	}

	@Test
	@DisplayName("필드 에러는 details에 필드와 사유를 담는다")
	void handleBusiness_fieldError_returnsDetails() throws Exception {
		mockMvc.perform(get("/test/out-of-range"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("OUT_OF_ALLOWED_RANGE"))
			.andExpect(jsonPath("$.details[0].field").value("prisonMonths"))
			.andExpect(jsonPath("$.details[0].reason").value("OUT_OF_ALLOWED_RANGE"));
	}

	@Test
	@DisplayName("@Valid 실패는 400 VALIDATION_ERROR와 필드별 제약 이름으로 응답한다")
	void handleBind_invalidBody_returnsValidationError() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"step\": 0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.details[0].field").value("step"))
			.andExpect(jsonPath("$.details[0].reason").value("Min"));
	}

	@Test
	@DisplayName("JSON 형식 오류와 경로 타입 오류는 400 VALIDATION_ERROR")
	void handleBadRequest_malformedInput_returnsValidationError() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"step\":"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/test/cases/abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("유니크 위반(동시 요청)은 409 INVALID_STATE, 그 밖의 제약 위반은 500")
	void handleDataIntegrity_uniqueViolation_returnsInvalidState() throws Exception {
		mockMvc.perform(get("/test/unique-violation"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INVALID_STATE"));
		mockMvc.perform(get("/test/check-violation"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
	}

	@Test
	@DisplayName("예상하지 못한 예외는 500 INTERNAL_ERROR, 내부 메시지를 응답에 노출하지 않는다")
	void handleUnexpected_runtimeException_hidesInternalMessage() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getMessage()));
	}

	@Test
	@DisplayName("지원하지 않는 메서드는 HTTP 상태(405)를 유지하고 VALIDATION_ERROR, Allow 헤더도 그대로 돌려준다 (리뷰 반영)")
	void handleSpringWeb_methodNotAllowed_keepsStatusAndAllowHeader() throws Exception {
		mockMvc.perform(delete("/test/unexpected"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(header().string("Allow", containsString("GET")));
	}

	@Test
	@DisplayName("필수 헤더 누락처럼 목록에 따로 적지 않은 Spring MVC 예외도 500이 아니라 400 VALIDATION_ERROR (리뷰 반영)")
	void handleExceptionInternal_missingRequiredHeader_returnsValidationErrorNotInternal() throws Exception {
		mockMvc.perform(get("/test/with-header"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("처리할 수 없는 Accept 헤더도 목록에 없지만 500이 아니라 406을 그대로 유지한다 (리뷰 반영)")
	void handleExceptionInternal_notAcceptable_keepsStatusInsteadOfInternalError() throws Exception {
		// 클라이언트가 JSON을 받지 않겠다고 선언했으므로(Accept: application/xml), 서버가 JSON 에러 본문을
		// 실어 보내지 않는 것 자체가 올바른 동작이다. 여기서 확인할 것은 500으로 새지 않는다는 것뿐이다.
		mockMvc.perform(get("/test/only-json").accept(MediaType.APPLICATION_XML))
			.andExpect(status().isNotAcceptable());
	}

	@RestController
	static class TestController {

		@GetMapping("/test/invalid-state")
		void invalidState() {
			throw new InvalidExperienceStateException(ExperienceStatus.PRE_JUDGED, "이미 제출했습니다.");
		}

		@GetMapping("/test/out-of-range")
		void outOfRange() {
			throw BusinessException.ofField(ErrorCode.OUT_OF_ALLOWED_RANGE, "prisonMonths");
		}

		@PostMapping("/test/body")
		void body(@Valid @RequestBody StepRequest request) {
		}

		@GetMapping("/test/cases/{caseId}")
		void typed(@PathVariable Long caseId) {
		}

		@GetMapping("/test/unique-violation")
		void uniqueViolation() {
			throw new DataIntegrityViolationException("duplicate", new SQLException("duplicate key", "23505"));
		}

		@GetMapping("/test/check-violation")
		void checkViolation() {
			throw new DataIntegrityViolationException("check", new SQLException("check violation", "23514"));
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("내부 구현 정보가 담긴 메시지");
		}

		@GetMapping("/test/with-header")
		void withHeader(@RequestHeader("X-Required") String header) {
		}

		@GetMapping(value = "/test/only-json", produces = MediaType.APPLICATION_JSON_VALUE)
		String onlyJson() {
			return "{}";
		}
	}

	record StepRequest(@NotNull @Min(2) Integer step) {
	}
}
