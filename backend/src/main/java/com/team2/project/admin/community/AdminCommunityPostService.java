package com.team2.project.admin.community;

import com.team2.project.admin.audit.AdminAuditAction;
import com.team2.project.admin.audit.AdminAuditLogger;
import com.team2.project.admin.audit.AuditTargetType;
import com.team2.project.admin.service.AdminPrincipal;
import com.team2.project.community.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 커뮤니티 글 삭제 (BE-167, A-42). 글 삭제와 감사 로그를 한 트랜잭션에 묶어, 기록 없는 삭제가 남지 않게 한다.
 * 감사 로그에는 글 ID와 글쓴이 회원 ID만 남긴다(본문 · 제목은 남기지 않는다)
 */
@Service
@RequiredArgsConstructor
public class AdminCommunityPostService {

	private final CommunityService communityService;
	private final AdminAuditLogger auditLogger;

	@Transactional
	public void delete(long postId, AdminPrincipal admin, HttpServletRequest request) {
		long authorId = communityService.deleteByAdmin(postId);
		auditLogger.record(admin, AdminAuditAction.COMMUNITY_POST_DELETE, AuditTargetType.COMMUNITY_POST, postId,
			Map.of("authorMemberId", authorId), request);
	}
}
