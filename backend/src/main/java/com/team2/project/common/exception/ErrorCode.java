package com.team2.project.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드 (API 명세 1-5 표와 1:1)
 * 새 에러는 여기에 한 줄 추가하고 BusinessException(ErrorCode)로 던진다.
 * 하위 예외 클래스는 추가 정보(필드)가 필요하거나 여러 곳에서 같은 의미로 던질 때만 만든다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "사건을 찾을 수 없습니다."),
	EXPERIENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "이 사건의 체험 기록이 없습니다."),
	INVALID_STATE(HttpStatus.CONFLICT, "지금 단계에서는 할 수 없는 요청입니다."),
	STEP_OUT_OF_ORDER(HttpStatus.CONFLICT, "섹션 확인 순서를 건너뛸 수 없습니다."),
	INVALID_RANGE_OPTION(HttpStatus.UNPROCESSABLE_CONTENT, "선택할 수 없는 형량 구간입니다."),
	INVALID_FACTOR(HttpStatus.UNPROCESSABLE_CONTENT, "판단 요소가 올바르지 않습니다."),
	TOO_MANY_FACTORS(HttpStatus.UNPROCESSABLE_CONTENT, "작용 요소는 2개까지 고를 수 있습니다."),
	INVALID_PENALTY_TYPE(HttpStatus.UNPROCESSABLE_CONTENT, "선택할 수 없는 형벌입니다."),
	OUT_OF_ALLOWED_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "선고할 수 있는 범위를 벗어났습니다."),
	INVALID_SUSPENSION(HttpStatus.UNPROCESSABLE_CONTENT, "집행유예를 적용할 수 없습니다."),
	// 관리자 API (확장 단계, BE-39). 로그인 실패는 계정이 있는지 드러내지 않도록 한 코드로 응답한다
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없거나 요청을 확인할 수 없습니다."),
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인 정보를 확인할 수 없습니다. 다시 로그인해 주세요."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."),
	// 관리자 검수 API (확장 단계, 7.4.7)
	JUDGMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "판결을 찾을 수 없습니다."),
	FACTOR_NOT_FOUND(HttpStatus.NOT_FOUND, "판단 요소를 찾을 수 없습니다."),
	DUPLICATE_TITLE(HttpStatus.CONFLICT, "다른 사건과 제목이 겹칩니다."),
	PUBLISH_NOT_READY(HttpStatus.CONFLICT, "공개 조건을 채우지 않았습니다."),
	// 성향 API (확장 단계, BE-72)
	PERSONALITY_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "성향 테스트 결과가 없습니다."),
	PERSONALITY_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "성향 유형을 찾을 수 없습니다."),
	// 성향카드 재심 (확장 단계, BE-104, API 명세 P-2 v0.50)
	BASELINE_RETAKE_USED(HttpStatus.CONFLICT, "성향카드 재심은 이미 한 번 했습니다."),
	// 사건 사진 업로드 · 프롬프트 추천 API (확장 단계, BE-91)
	PROMPT_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "프롬프트 추천에 실패했습니다."),
	FILE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "사진 업로드에 실패했습니다."),
	INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "사진 파일이 올바르지 않습니다."),
	// 발단 · 결론 사진 역할 칸 · 삭제 API (확장 단계, BE-78 2차 검토 · BE-101)
	INCIDENT_PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "그 역할의 사진이 없습니다."),
	// 신문 지면 콘텐츠 생성 API (확장 단계, BE-92)
	PAPER_NOT_FOUND(HttpStatus.NOT_FOUND, "신문 지면 콘텐츠가 없습니다."),
	// 프로필 꾸미기 (확장 단계, BE-96). details에 잠긴 필드를 담는다
	DECO_LOCKED(HttpStatus.CONFLICT, "아직 열리지 않은 꾸미기입니다."),
	// 레벨 · 직업 (확장 단계, BE-106)
	JOB_LOCKED(HttpStatus.CONFLICT, "아직 열리지 않은 직업입니다."),
	// 사건 등록 API (확장 단계, BE-93, API 명세 A-14 · A-15)
	DUPLICATE_SOURCE(HttpStatus.CONFLICT, "이미 등록한 사건번호입니다."),
	IMPORT_IN_PROGRESS(HttpStatus.CONFLICT, "다른 사건을 등록하는 중입니다."),
	IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "등록 작업을 찾을 수 없습니다."),
	IMPORT_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "사건 등록을 지금 쓸 수 없습니다."),
	IMPORT_NOT_READY(HttpStatus.CONFLICT, "아직 적재할 수 없는 등록 작업입니다."),
	IMPORT_RESULT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "생성 결과를 사건으로 만들 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;

	private final String message;	// 기본 메시지 (화면에 그대로 띄우지 않는다, API 명세 1-4)
}
