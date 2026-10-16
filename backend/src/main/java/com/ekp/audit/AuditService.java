package com.ekp.audit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.UUID;

/** Audit actors must be committed before a separate audit transaction references them. */
@Service
public class AuditService {
    private static final Logger log=LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository repository;
    private final TransactionTemplate transaction;
    public AuditService(AuditLogRepository repository, PlatformTransactionManager manager) {
        this.repository=repository;
        this.transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void record(UUID actorId, AuditAction action, String resourceType,
                       UUID resourceId, Map<String,Object> metadata, String ipAddress) {
        Runnable write=() -> {
            try {
                transaction.executeWithoutResult(status -> repository.saveAndFlush(new AuditLog(
                    actorId,action.name(),resourceType,resourceId,metadata,ipAddress)));
            } catch (Exception ex) { log.error("Failed to write audit log for action {}",action,ex); }
        };
        if (actorId!=null && TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { write.run(); }
            });
        } else write.run(); // Anonymous failed-login audits survive caller rollback.
    }
    public void record(UUID actorId, AuditAction action, String ipAddress) {
        record(actorId,action,null,null,Map.of(),ipAddress);
    }
}
