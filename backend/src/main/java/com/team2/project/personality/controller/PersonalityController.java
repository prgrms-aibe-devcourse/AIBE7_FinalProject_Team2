package com.team2.project.personality.controller;

import com.team2.project.common.web.AnonymousIdCookie;
import com.team2.project.personality.dto.MyPageResponse;
import com.team2.project.personality.dto.PersonalityQuestionsResponse;
import com.team2.project.personality.dto.PersonalityResultResponse;
import com.team2.project.personality.dto.PersonalitySubmitRequest;
import com.team2.project.personality.dto.PersonalityTypeResponse;
import com.team2.project.personality.service.MyPageService;
import com.team2.project.personality.service.PersonalityService;
import com.team2.project.personality.service.PersonalityService.SubmitResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판결 성향 API (API 명세 9장 P-1 ~ P-5, WBS 7.6.4). 익명 ID 쿠키 기준이고, 4글자 코드 · 축 글자는 응답에 없다(#119)
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PersonalityController {

	private final PersonalityService personalityService;
	private final MyPageService myPageService;
	private final AnonymousIdCookie anonymousIdCookie;

	/** P-1 문항 목록 */
	@GetMapping("/personality/questions")
	public PersonalityQuestionsResponse getQuestions() {
		return personalityService.getQuestions();
	}

	/** P-2 응답 제출 — 201, 쿠키가 없었으면 Set-Cookie로 익명 ID 발급 */
	@PostMapping("/personality/results")
	@ResponseStatus(HttpStatus.CREATED)
	public PersonalityResultResponse submit(@Valid @RequestBody PersonalitySubmitRequest body, HttpServletRequest request,
		HttpServletResponse response) {
		SubmitResult result = personalityService.submit(anonymousIdCookie.read(request), body);
		if (result.issuedCookieId() != null) {
			anonymousIdCookie.write(response, result.issuedCookieId());
		}
		return result.result();
	}

	/** P-3 내 현재 결과 */
	@GetMapping("/personality/results/me")
	public PersonalityResultResponse getMine(HttpServletRequest request) {
		return personalityService.getMine(anonymousIdCookie.read(request));
	}

	/** P-4 공유용 유형 설명 (공개, 쿠키를 읽지 않는다) */
	@GetMapping("/personality/types/{shareId}")
	public PersonalityTypeResponse getType(@PathVariable String shareId) {
		return personalityService.getType(shareId);
	}

	/** P-5 마이페이지 */
	@GetMapping("/my-page")
	public MyPageResponse getMyPage(HttpServletRequest request) {
		return myPageService.get(anonymousIdCookie.read(request));
	}
}
