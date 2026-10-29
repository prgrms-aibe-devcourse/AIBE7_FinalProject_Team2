package com.team2.project.community.dto;

import java.util.List;

/**
 * C-3 글쓰기 요청. 값 검사는 서비스가 한다(틀린 칸을 모두 모아 details로 내려 준다).
 * 타입이 틀린 값(예: caseId에 문자열)은 본문 해석 단계에서 400이 된다.
 *
 * @param images       사진 주소. 사진 올리기는 아직 없어 빈 배열이거나 생략만 받는다
 * @param shareVerdict true면 서버가 내 판결을 붙인다(화면은 판결 값을 보내지 않는다)
 */
public record CreatePostRequest(String category, Long caseId, Object title, Object body, List<Object> images,
	Boolean shareVerdict) {
}
