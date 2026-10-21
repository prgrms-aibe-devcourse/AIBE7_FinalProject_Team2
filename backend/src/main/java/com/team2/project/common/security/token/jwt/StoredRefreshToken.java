package com.team2.project.common.security.token.jwt;

import java.util.Set;

/** Redis에 저장하는 리프레시 토큰 내용(JSON) (확장 단계, BE-94 · WBS 7.4.3) */
record StoredRefreshToken(long memberId, Set<String> roles) {
}
