package com.team2.project.judgment.repository;

/** 사전 판단 구간별 선택 수 집계 결과 (API 14 독자 첫인상 분포, FE-34). */
public interface RangeOptionCount {

	Long getRangeOptionId();

	long getCount();
}
