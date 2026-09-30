package com.team2.project.experience.repository;

/** 사건별 참여자 수 집계 결과. */
public interface CaseParticipantCount {

	Long getCaseId();

	long getParticipantCount();
}
