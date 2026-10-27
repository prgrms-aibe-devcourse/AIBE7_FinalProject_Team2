package com.team2.project.level.domain;

import java.util.Arrays;
import java.util.Optional;

/** 직업 11등급 (BE-106). Lv1 · 10 · 20 … 100에서 하나씩 열린다. 이름은 화면에 그대로 쓴다 */
public enum Job {

	NONE("무직", 1),
	LAW_STUDENT("로스쿨생", 10),
	TRAINEE("연수원생", 20),
	PROBATION_JUDGE("수습 판사", 30),
	JUDGE("판사", 40),
	PRESIDING_JUDGE("부장판사", 50),
	HIGH_COURT_JUDGE("고등법원 판사", 60),
	DISTRICT_COURT_CHIEF("지방법원장", 70),
	HIGH_COURT_CHIEF("고등법원장", 80),
	JUSTICE("대법관", 90),
	CHIEF_JUSTICE("대법원장", 100);

	private final String label;
	private final int unlockLevel;

	Job(String label, int unlockLevel) {
		this.label = label;
		this.unlockLevel = unlockLevel;
	}

	public String label() {
		return label;
	}

	public int unlockLevel() {
		return unlockLevel;
	}

	public boolean unlockedAt(int level) {
		return level >= unlockLevel;
	}

	/** 그 레벨에서 열린 가장 높은 직업 */
	public static Job highestAt(int level) {
		Job best = NONE;
		for (Job job : values()) {
			if (job.unlockedAt(level)) {
				best = job;
			}
		}
		return best;
	}

	public static Optional<Job> fromCode(String code) {
		return Arrays.stream(values()).filter(job -> job.name().equals(code)).findFirst();
	}
}
