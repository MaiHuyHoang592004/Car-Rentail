package com.rentflow.audit.service;

import java.util.UUID;

public interface AuditLogService {

    void record(
            UUID actorUserId,
            String actorType,
            String action,
            String targetType,
            UUID targetId,
            String status,
            String detailsJson);
}
