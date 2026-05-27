package com.rentflow.audit.service;

import com.rentflow.audit.entity.AuditLog;
import com.rentflow.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultAuditLogService implements AuditLogService {

    private final AuditLogRepository repository;

    public DefaultAuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(
            UUID actorUserId,
            String actorType,
            String action,
            String targetType,
            UUID targetId,
            String status,
            String detailsJson) {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setActorType(actorType);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setStatus(status);
        log.setDetails(detailsJson);
        repository.save(log);
    }
}
