package com.team2.project.community.dto;

import com.team2.project.level.dto.LevelStatus.JobView;
import com.team2.project.personality.dto.PersonalityTypeResponse;
import com.team2.project.profile.domain.ProfileDecoValues;

/**
 * 글쓴이 명함 (API 명세 12-1, BE-115 · FE-88 · FE-116). 글 · 댓글의 author와 레벨 랭킹이 같은 값을 쓴다.
 *
 * @param intro          한 줄 소개 (P-8 bio)
 * @param level          레벨 (L-1과 같은 값)
 * @param job            카드에 보이는 직업 (L-1 job)
 * @param cardLine       프로필카드 아래 줄 (P-8 cardLine · cardLevel)
 * @param completedCount 끝까지 체험한 사건 수
 * @param profileCard    프로필카드 꾸미기 (P-7과 같은 칸)
 * @param shownType      공개 성향카드. 보여 줄 성향이 없으면 null
 */
public record AuthorCard(String nickname, String intro, int level, JobView job, CardLine cardLine, int completedCount,
	ProfileDecoValues profileCard, ShownType shownType) {

	/** @param line type(성향) · signature(한 줄 소개) · job(직업), @param showLevel 레벨을 윗줄에 붙일지 */
	public record CardLine(String line, boolean showLevel) {
	}

	/**
	 * @param slot 실제로 보여 주는 칸 initial(초심) · retake(재심) · current(최근). 고른 칸이 비면 최근 → 초심 → 재심 순으로 대신 고른다
	 * @param deco 성향카드 꾸미기. 아직 서버에 저장하지 않아(화면 브라우저 저장, FE-82) 늘 null — 화면이 기본 꾸미기로 그린다
	 */
	public record ShownType(String slot, PersonalityTypeResponse type, Object deco) {
	}
}
