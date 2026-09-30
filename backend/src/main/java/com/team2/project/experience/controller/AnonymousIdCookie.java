package com.team2.project.experience.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.ResponseCookie;

/** 익명 ID 쿠키 NLNB_AID (API 명세서 1-2). */
final class AnonymousIdCookie {

	static final String NAME = "NLNB_AID";

	private static final Duration MAX_AGE = Duration.ofDays(365);

	private AnonymousIdCookie() {
	}

	/** UUID 형식이 아닌 값은 쿠키가 없는 것으로 취급한다. */
	static UUID parse(String value) {
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static ResponseCookie issue(UUID anonymousId) {
		return ResponseCookie.from(NAME, anonymousId.toString())
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(MAX_AGE)
				.build();
	}
}
