package com.team2.project.judgment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team2.project.judgment.domain.Judgment;

public interface JudgmentRepository extends JpaRepository<Judgment, Long> {
}
