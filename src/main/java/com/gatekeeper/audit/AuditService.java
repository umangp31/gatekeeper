package com.gatekeeper.audit;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(UUID actorId, String action, String target, Map<String, Object> payload) {
        auditLogRepository.save(new AuditLog(UUID.randomUUID(), actorId, action, target, payload));
    }
}
