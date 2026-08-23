package com.amit.jobagent.audit;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
@Service
public class AuditService {
    private final AuditEventRepository repository;
    public AuditService(AuditEventRepository repository) { this.repository=repository; }
    public void record(AuditEventType type,String aggregateType,UUID aggregateId,String metadata) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        var actor=authentication==null ? "system" : authentication.getName();
        repository.save(new AuditEvent(type,aggregateType,aggregateId,actor,metadata==null ? "{}" : metadata));
    }
}
