package com.team2.project.admin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
